package emu.server.host

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.server.gateway.GatewayService
import emu.server.js5.Js5Service
import emu.server.login.LoginService
import emu.persistence.postgres.character.CharacterSaveWriterConfig
import emu.persistence.postgres.database.PostgresMigrator
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import emu.server.world.GameService
import emu.server.world.map.CacheCollisionMap
import emu.server.world.network.loadHuffmanCodec
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
    val collision =
        CacheCollisionMap(
            CacheMapRepository(assets.store),
            CacheObjectDefinitionRepository(assets.store),
        )
    val huffman = loadHuffmanCodec(assets.store)
    val koinApplication =
        koinApplication {
            allowOverride(false)
            modules(
                persistenceModule(
                    config.database,
                    CharacterSaveWriterConfig(capacity = config.world.maxConcurrentSessions),
                ),
                js5Module(assets.store, buildJs5CodecRepository(), config.js5),
                loginModule(assets.rsaKeyPair, config.login),
                gameModule(buildGameCodecRepository(), collision, huffman, config.world),
            )
        }
    val koin = koinApplication.koin
    try {
        withContext(Dispatchers.IO) { koin.get<PostgresMigrator>().migrate() }
        val persistenceReady = System.nanoTime()

        val js5 = koin.get<Js5Service>()
        val login = koin.get<LoginService>()
        val world = koin.get<GameService>()
        world.start()
        var shutdownHook: Thread? = null
        var gatewayJob: Job? = null
        var worldMonitor: Job? = null
        val listener =
            try {
                val coordinator = ServerCoordinator(js5, login, world, config.coordinator)
                GatewayService(config.gateway, coordinator.gatewayRoutes()).bind()
            } catch (failure: Throwable) {
                withContext(NonCancellable) { world.stop() }
                throw failure
            }
        try {
            val listening = System.nanoTime()
            val stop = CompletableDeferred<Unit>()
            shutdownHook = Thread({ stop.complete(Unit) }, "server-shutdown")
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            gatewayJob = launch { listener.run() }
            worldMonitor = launch {
                world.awaitTermination()
                error("world server stopped unexpectedly")
            }
            logger.info {
                "server listening on ${listener.localAddress}; startup total=${millis(startupStarted, listening)}ms " +
                    "(assets=${millis(startupStarted, assetsReady)}ms, " +
                    "persistence=${millis(assetsReady, persistenceReady)}ms, " +
                    "services+bind=${millis(persistenceReady, listening)}ms)"
            }
            stop.await()
        } finally {
            shutdownServer(world, listener, gatewayJob, worldMonitor, shutdownHook)
        }
    } finally {
        koinApplication.close()
    }
}

private fun millis(startNanos: Long, endNanos: Long): Long = (endNanos - startNanos) / 1_000_000
