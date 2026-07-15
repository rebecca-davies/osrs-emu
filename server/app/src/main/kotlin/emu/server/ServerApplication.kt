package emu.server

import emu.server.game.createGameServer
import emu.server.gateway.GatewayService
import emu.server.js5.BoundedJs5Server
import emu.server.js5.handler.Js5RequestHandler
import emu.server.login.BoundedLoginServer
import emu.server.login.auth.AccountAuthenticator
import emu.server.login.auth.BcryptPasswordHasher
import emu.persistence.account.AccountStore
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.persistence.postgres.chat.ChatAuditWriter
import emu.persistence.postgres.database.PostgresMigrator
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
    withContext(Dispatchers.IO) { koin.get<PostgresMigrator>().migrate() }
    val persistenceReady = System.nanoTime()

    val authenticator = AccountAuthenticator(koin.get<AccountStore>(), BcryptPasswordHasher(config.authentication))
    val characters = koin.get<CharacterStore>()
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
    val game =
        createGameServer(
            store = assets.store,
            codecs = buildGameCodecRepository(),
            characters = characters,
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
    val worldMonitor = launch {
        game.awaitTermination()
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
