package emu.server

import emu.server.game.BoundedGameServer
import emu.server.gateway.GatewayService
import emu.server.js5.BoundedJs5Server
import emu.server.js5.handler.Js5RequestHandler
import emu.server.login.BoundedLoginServer
import emu.server.login.LoginAuthenticator
import emu.persistence.AccountService
import emu.persistence.ChatAuditSink
import emu.persistence.ChatAuditWriter
import emu.persistence.PlayerRepository
import emu.persistence.PostgresDatabase
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.js5.buildJs5CodecRepository
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
    val koinApplication =
        koinApplication {
            modules(persistenceModule(config.database))
        }
    val koin = koinApplication.koin
    val database = koin.get<PostgresDatabase>()
    withContext(Dispatchers.IO) { database.migrate() }
    val persistenceReady = System.nanoTime()

    val accounts = koin.get<AccountService>()
    val players = koin.get<PlayerRepository>()
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
            authenticator = LoginAuthenticator { username, password -> accounts.authenticateLogin(username, password) },
            config = config.login,
        )
    val game =
        BoundedGameServer(
            store = assets.store,
            codecs = buildGameCodecRepository(),
            players = players,
            chatAudit = koin.get<ChatAuditSink>(),
            config = config.game,
        )
    game.start()
    val coordinator = ServerCoordinator(js5, login, game, config.coordinator)
    val listener = GatewayService(config.gateway, coordinator.gatewayRoutes()).bind()
    val listening = System.nanoTime()
    val stop = CompletableDeferred<Unit>()
    val shutdownHook = Thread({ stop.complete(Unit) }, "server-shutdown")
    Runtime.getRuntime().addShutdownHook(shutdownHook)
    val gatewayJob = launch { listener.run() }
    logger.info {
        "server listening on ${listener.localAddress}; startup total=${millis(startupStarted, listening)}ms " +
            "(assets=${millis(startupStarted, assetsReady)}ms, " +
            "persistence=${millis(assetsReady, persistenceReady)}ms, " +
            "services+bind=${millis(persistenceReady, listening)}ms)"
    }
    try {
        stop.await()
    } finally {
        gatewayJob.cancel()
        listener.close()
        gatewayJob.cancelAndJoin()
        game.stop()
        login.close()
        js5.close()
        game.close()
        chatAuditWriter.close()
        koinApplication.close()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}

private fun millis(startNanos: Long, endNanos: Long): Long = (endNanos - startNanos) / 1_000_000
