package emu.server.host

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.server.gateway.GatewayService
import emu.server.js5.BoundedJs5Server
import emu.server.js5.handler.Js5RequestHandler
import emu.server.login.BoundedLoginServer
import emu.server.login.auth.AccountAuthenticator
import emu.server.login.auth.BcryptPasswordHasher
import emu.persistence.account.AccountStore
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresMigrator
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import emu.server.world.WorldServer
import emu.server.world.map.CacheCollisionMap
import emu.server.world.network.loadHuffmanCodec
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.dsl.koinApplication

private val logger = KotlinLogging.logger {}

/** Runs the single-process deployment by composing independently owned protocol services. */
suspend fun runServer(config: ServerConfig): Unit = coroutineScope {
    val startupStarted = System.nanoTime()
    val assets = loadRuntimeAssets(config)
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
                persistenceModule(config.database),
                worldModule(buildGameCodecRepository(), collision, huffman, config.world),
            )
        }
    val koin = koinApplication.koin
    withContext(Dispatchers.IO) { koin.get<PostgresMigrator>().migrate() }
    val persistenceReady = System.nanoTime()

    val authenticator = AccountAuthenticator(koin.get<AccountStore>(), BcryptPasswordHasher(config.authentication))
    val chatAuditWriter = koin.get<ChatAuditWriter>()
    val js5 =
        BoundedJs5Server(
            codecs = buildJs5CodecRepository(),
            requests = Js5RequestHandler(assets.store),
            config = config.js5,
        )
    val login =
        BoundedLoginServer(
            rsaKeyPair = assets.rsaKeyPair,
            authenticator = authenticator,
            config = config.login,
        )
    val world = koin.get<WorldServer>()
    world.start()
    val coordinator = ServerCoordinator(js5, login, world, config.coordinator)
    val listener = GatewayService(config.gateway, coordinator.gatewayRoutes()).bind()
    val listening = System.nanoTime()
    val stop = CompletableDeferred<Unit>()
    val shutdownHook = Thread({ stop.complete(Unit) }, "server-shutdown")
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    val gatewayJob = launch { listener.run() }
    val worldMonitor = launch {
        world.awaitTermination()
        error("world server stopped unexpectedly")
    }
    logger.info {
        "server listening on ${listener.localAddress}; startup total=${millis(startupStarted, listening)}ms " +
            "(assets=${millis(startupStarted, assetsReady)}ms, " +
            "persistence=${millis(assetsReady, persistenceReady)}ms, " +
            "services+bind=${millis(persistenceReady, listening)}ms)"
    }
    try {
        stop.await()
    } finally {
        worldMonitor.cancel()
        gatewayJob.cancel()
        listener.close()
        worldMonitor.cancelAndJoin()
        gatewayJob.cancelAndJoin()
        world.stop()
        login.close()
        js5.close()
        chatAuditWriter.close()
        koinApplication.close()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}

private fun millis(startNanos: Long, endNanos: Long): Long = (endNanos - startNanos) / 1_000_000
