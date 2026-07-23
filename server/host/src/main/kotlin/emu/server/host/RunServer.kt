package emu.server.host

import emu.cache.def.CacheItemDefinitionCatalog
import emu.cache.def.CacheNpcDefinitionCatalog
import emu.cache.def.CacheVarbitDefinitionCatalog
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.persistence.postgres.character.writeback.CharacterSaveWriterConfig
import emu.persistence.postgres.database.PostgresMigrator
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import emu.server.bot.BotService
import emu.server.game.GameService
import emu.server.game.network.chat.loadHuffmanCodec
import emu.server.game.world.map.CacheCollisionMap
import emu.server.game.world.map.CacheLocRepository
import emu.server.game.world.obj.CacheNamedObjEnumCatalog
import emu.server.game.world.obj.CacheObjCatalog
import emu.server.game.world.npc.CacheNpcCatalog
import emu.server.gateway.GatewayListener
import emu.server.host.asset.loadRuntimeAssets
import emu.server.host.composition.botModule
import emu.server.host.composition.botEndpointOrNull
import emu.server.host.composition.gameModule
import emu.server.host.composition.js5Module
import emu.server.host.composition.loginModule
import emu.server.host.composition.persistenceModule
import emu.server.host.config.ServerConfig
import emu.server.host.handoff.ServerCoordinator
import emu.server.host.lifecycle.shutdownServer
import emu.server.js5.Js5Service
import emu.server.login.LoginService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.koinApplication

private val logger = KotlinLogging.logger {}

/** Runs the composed protocol services and their shared process lifecycle. */
suspend fun runServer(config: ServerConfig): Unit = coroutineScope {
    val startupStarted = System.nanoTime()
    val assets = loadRuntimeAssets(config.assets)
    val assetsReady = System.nanoTime()
    val maps = CacheMapRepository(assets.store)
    val locTypes = CacheObjectDefinitionRepository(assets.store)
    val collision = CacheCollisionMap(maps, locTypes)
    val locs = CacheLocRepository(maps, locTypes)
    val objs = CacheObjCatalog(CacheItemDefinitionCatalog(assets.store).definitions)
    val objEnums = CacheNamedObjEnumCatalog(assets.store)
    val npcTypes =
        CacheNpcCatalog(
            CacheNpcDefinitionCatalog(assets.store).definitions,
            CacheVarbitDefinitionCatalog(assets.store).definitions,
        )
    val huffman = loadHuffmanCodec(assets.store)
    val koinApplication =
        koinApplication {
            allowOverride(false)
            modules(
                persistenceModule(
                    config.database,
                    CharacterSaveWriterConfig(capacity = config.game.maxConcurrentSessions),
                ),
                js5Module(assets.store, buildJs5CodecRepository(), config.js5),
                loginModule(assets.rsaKeyPair, config.login),
                botModule(config.bots, assets.rsaKeyPair.publicKey),
                gameModule(
                    buildGameCodecRepository(),
                    collision,
                    locs,
                    objs,
                    objEnums,
                    npcTypes,
                    huffman,
                    config.game,
                ),
            )
        }
    val koin = koinApplication.koin
    try {
        withContext(Dispatchers.IO) { koin.get<PostgresMigrator>().migrate() }
        val persistenceReady = System.nanoTime()

        val js5 = koin.get<Js5Service>()
        val login = koin.get<LoginService>()
        val game = koin.get<GameService>()
        val bots = koin.get<BotService>()
        game.start()
        var shutdownHook: Thread? = null
        var gatewayJob: Job? = null
        var gameMonitor: Job? = null
        val listener =
            try {
                val coordinator = ServerCoordinator(js5, login, game, config.coordinator)
                GatewayListener.bind(config.gateway, coordinator.gatewayRoutes())
            } catch (failure: Throwable) {
                withContext(NonCancellable) { game.stop() }
                throw failure
            }
        try {
            val listening = System.nanoTime()
            val stop = CompletableDeferred<Unit>()
            shutdownHook = Thread({ stop.complete(Unit) }, "server-shutdown")
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            gatewayJob = launch { listener.run() }
            val botEndpoint = listener.localEndpoint.botEndpointOrNull()
            if (botEndpoint == null) {
                logger.warn {
                    "headless bot clients unavailable because gateway endpoint " +
                        "${listener.localAddress} has no loopback route"
                }
            } else {
                bots.start(botEndpoint)
            }
            gameMonitor = launch {
                game.awaitTermination()
                error("game server stopped unexpectedly")
            }
            logger.info {
                "server listening on ${listener.localAddress}; startup total=${millis(startupStarted, listening)}ms " +
                    "(assets=${millis(startupStarted, assetsReady)}ms, " +
                    "persistence=${millis(assetsReady, persistenceReady)}ms, " +
                    "services+bind=${millis(persistenceReady, listening)}ms)"
            }
            stop.await()
        } finally {
            shutdownServer(bots, game, listener, gatewayJob, gameMonitor, shutdownHook)
        }
    } finally {
        koinApplication.close()
    }
}

private fun millis(startNanos: Long, endNanos: Long): Long = (endNanos - startNanos) / 1_000_000
