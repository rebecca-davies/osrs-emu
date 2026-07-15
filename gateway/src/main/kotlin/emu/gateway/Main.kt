package emu.gateway

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.store.FlatFileStore
import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import emu.crypto.XorStreamCipher
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.OpenCollisionMap
import emu.gateway.js5.installJs5Handlers
import emu.gateway.js5.performHandshake
import emu.gateway.login.AuthenticatedGameLogin
import emu.gateway.login.GAME_IDLE_TIMEOUT
import emu.gateway.login.SPAWN_PLANE
import emu.gateway.login.SPAWN_X
import emu.gateway.login.SPAWN_Y
import emu.gateway.login.ServerRsaKeyFile
import emu.gateway.login.performLoginBlock
import emu.gateway.login.performLoginInit
import emu.gateway.login.runGameStage
import emu.gateway.map.CacheCollisionMap
import emu.gateway.game.loadHuffmanCodec
import emu.gateway.world.WorldRuntime
import emu.compression.HuffmanCodec
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import emu.protocol.osrs239.js5.js5Module
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.login.loginModule
import emu.protocol.osrs239.login.prot.LoginProt
import emu.persistence.AccountService
import emu.persistence.AuthenticationResult
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRepository
import emu.persistence.PostgresDatabase
import emu.persistence.ChatAuditSink
import emu.persistence.ChatAuditWriter
import emu.persistence.persistenceModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.dsl.koinApplication
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Deadline for every pre-game phase of a connection: the first-opcode read, the JS5 handshake
 * reply, and (for login) the opcode-14 init through the opcode-16/18 login block. A real client
 * completes all of these in well under a second, so this is set short — it bounds a slowloris
 * connection (one that opens the socket and never sends the next byte, or stalls mid-login) to a
 * small window instead of holding a coroutine + file descriptor forever (CLAUDE.md §10). It does
 * NOT bound the JS5 asset-serving pipeline or the GAME stage that follow a successful handshake —
 * see [emu.gateway.login.GAME_IDLE_TIMEOUT] for the GAME stage's much more generous idle deadline.
 */
internal val HANDSHAKE_TIMEOUT: Duration = 15.seconds

/**
 * Gateway entry point: binds the JS5/login TCP port, then accepts connections forever, dispatching
 * each one to [handleConnection] on its own coroutine. All per-connection state (the ISAAC/XOR
 * cipher, the socket) is created inside [handleConnection]; everything built here ([codecs], the
 * RSA keypair, [koin]) is immutable and shared read-only across every connection.
 *
 * Koin is started once here with every domain's codec module plus [gatewayModule] (CLAUDE.md §5a
 * addendum) — `net-core` never sees Koin; only this service layer and `protocol-osrs239` do. Each
 * `CodecRepository` is then assembled by COLLECTING the codecs Koin holds
 * ([emu.protocol.osrs239.buildCodecRepository]) rather than a `bindDecoder(...).bindEncoder(...)`
 * chain.
 */
fun main() = runBlocking {
    val startupStarted = System.nanoTime()
    val store = FlatFileStore(cacheDir())
    val collisionMap = loadCollisionMap(store)
    val huffman = loadHuffmanCodec(store)
    val cacheReady = System.nanoTime()
    val rsaKeyPair = loadServerRsaKeyPair()
    val bootstrapReady = System.nanoTime()
    val koin = startKoin {
        modules(js5Module, loginModule, gameModule, persistenceModule, gatewayModule(store, rsaKeyPair))
    }.koin
    val database = koin.get<PostgresDatabase>()
    withContext(Dispatchers.IO) { database.migrate() }
    val databaseReady = System.nanoTime()
    val accounts = koin.get<AccountService>()
    val players = koin.get<PlayerRepository>()
    val chatAuditWriter = koin.get<ChatAuditWriter>()
    val chatAudit: ChatAuditSink = chatAuditWriter
    Runtime.getRuntime().addShutdownHook(Thread(chatAuditWriter::close, "chat-audit-shutdown"))
    val codecs = buildJs5CodecRepository()
    val gameCodecs = buildGameCodecRepository()
    val worldRuntime = WorldRuntime()
    launch(Dispatchers.Default) { worldRuntime.run() }

    val selector = SelectorManager(Dispatchers.IO)
    val server = aSocket(selector).tcp().bind(InetSocketAddress("0.0.0.0", 43594))
    val listening = System.nanoTime()
    logger.info {
        "gateway listening on 43594; startup total=${millis(startupStarted, listening)}ms " +
            "(cache=${millis(startupStarted, cacheReady)}ms, " +
            "bootstrap=${millis(cacheReady, bootstrapReady)}ms, " +
            "database=${millis(bootstrapReady, databaseReady)}ms, " +
            "codecs+bind=${millis(databaseReady, listening)}ms)"
    }
    while (true) {
        val conn = server.accept()
        launch {
            handleConnection(
                conn,
                store,
                codecs,
                gameCodecs,
                rsaKeyPair,
                worldRuntime,
                koin = koin,
                collisionMap = collisionMap,
                authenticate = { username, password ->
                    accounts.loginOrCreate(
                        username,
                        password,
                        PlayerPosition(SPAWN_X, SPAWN_Y, SPAWN_PLANE),
                    )
                },
                saveSession = players::saveSession,
                huffman = huffman,
                chatAudit = chatAudit,
            )
        }
    }
}

private fun millis(startNanos: Long, endNanos: Long): Long = (endNanos - startNanos) / 1_000_000

/**
 * What a successful pre-game handshake hands off to run *outside* [HANDSHAKE_TIMEOUT]'s deadline:
 * either the JS5 asset-serving pipeline, or the GAME stage of a newly logged-in connection. Kept
 * private — it never appears in an `internal`/public signature (see [handleConnection]).
 */
private sealed interface PostHandshake {
    data class Js5Pipeline(val cipher: XorStreamCipher) : PostHandshake
    data class GameStage(val login: AuthenticatedGameLogin) : PostHandshake
}

/**
 * Owns one accepted connection end to end: reads the first opcode and routes to the JS5 or login
 * flow, then guarantees the socket is closed on every exit path — success, an unhandled opcode, a
 * timeout, or an exception — so a misbehaving client can never leak the file descriptor or kill the
 * accept loop in [main].
 *
 * Everything through the first opcode and (for JS5) the handshake reply or (for login) the opcode-16/18
 * login block runs inside [handshakeTimeout] — see that constant's doc for why. The JS5 pipeline and
 * the GAME stage that a successful handshake hands off to via [PostHandshake] deliberately run
 * *outside* that deadline: the former can legitimately serve a long asset-download session, and the
 * latter has its own, much more generous idle deadline ([gameIdleTimeout]) that resets per packet.
 *
 * [handshakeTimeout] and [gameIdleTimeout] default to the real constants; tests override them with a
 * tiny duration so a timeout path can be exercised without a real-time sleep. [koin] defaults to a
 * fresh, connection-scoped instance holding just [gatewayModule] so callers that do not care about
 * DI (e.g. [emu.gateway.ConnectionTimeoutTest]) do not need to construct one; [main] instead passes
 * its single already-started instance, shared read-only across every connection.
 */
internal suspend fun handleConnection(
    conn: Socket,
    store: Store,
    codecs: CodecRepository,
    gameCodecs: CodecRepository,
    rsaKeyPair: RsaKeyPair?,
    worldRuntime: WorldRuntime,
    handshakeTimeout: Duration = HANDSHAKE_TIMEOUT,
    gameIdleTimeout: Duration = GAME_IDLE_TIMEOUT,
    koin: Koin = koinApplication { modules(gatewayModule(store, rsaKeyPair)) }.koin,
    collisionMap: CollisionMap = OpenCollisionMap,
    authenticate: (String, CharArray) -> AuthenticationResult = { _, _ ->
        AuthenticationResult.InvalidCredentials
    },
    saveSession: (Long, PlayerPosition, Long, Map<Int, Int>) -> Unit = { _, _, _, _ -> },
    huffman: HuffmanCodec = HuffmanCodec(ByteArray(256) { 8 }),
    chatAudit: ChatAuditSink = ChatAuditSink { true },
) {
    try {
        val r = conn.openReadChannel()
        val w = conn.openWriteChannel(autoFlush = false)
        val next = withTimeout(handshakeTimeout) {
            when (val opcode = r.readByte().toInt() and 0xFF) {
                Js5Prot.HANDSHAKE.opcode -> handshakeJs5(r, w)
                LoginProt.INIT.opcode -> handshakeLogin(r, w, rsaKeyPair, authenticate)
                else -> { logger.warn { "unknown first opcode $opcode; closing connection" }; null }
            }
        }
        when (next) {
            is PostHandshake.Js5Pipeline -> runJs5Pipeline(r, w, codecs, koin, next.cipher)
            is PostHandshake.GameStage ->
                runGameStage(
                    r,
                    w,
                    next.login.inbound,
                    next.login.outbound,
                    gameCodecs,
                    player = next.login.player,
                    worldRuntime = worldRuntime,
                    saveSession = saveSession,
                    huffman = huffman,
                    chatAudit = chatAudit,
                    idleTimeout = gameIdleTimeout,
                    collisionMap = collisionMap,
                )
            null -> {}
        }
    } catch (e: TimeoutCancellationException) {
        // A legit client finishes the pre-game phase in well under a second; anything else is
        // either dead air (slowloris) or a stalled mid-login client. Either way, close cleanly
        // rather than holding the coroutine + fd forever (CLAUDE.md §10).
        logger.warn { "connection exceeded the $handshakeTimeout handshake/login deadline; closing" }
    } catch (t: Throwable) {
        // Swallow so one bad client cannot kill the accept loop; still logged so a login/JS5
        // failure is visible instead of looking like total silence.
        logger.error(t) { "connection handler threw" }
    } finally {
        conn.close()
    }
}

/**
 * Performs the JS5 revision handshake for a connection whose first opcode was
 * [Js5Prot.HANDSHAKE.opcode]. On success, returns the [PostHandshake.Js5Pipeline] action carrying
 * the per-connection [XorStreamCipher] the pipeline (and the control-opcode-4 handler within it)
 * will share; returns null on a revision mismatch (the caller's `finally` closes the connection).
 */
private suspend fun handshakeJs5(r: ByteReadChannel, w: ByteWriteChannel): PostHandshake? =
    if (performHandshake(r, w)) PostHandshake.Js5Pipeline(XorStreamCipher()) else null

/**
 * Performs opcode 14 (login init) through the opcode-16/18 login block for a connection whose first
 * opcode was [LoginProt.INIT.opcode]. On a successful login block, returns the
 * [PostHandshake.GameStage] action carrying the authenticated player and ISAAC ciphers; returns null (connection
 * left for the caller to close) if no server RSA keypair was loaded at startup, the opcode after
 * INIT is neither [LoginProt.NEW_LOGIN] nor [LoginProt.RECONNECT], or the login block itself fails.
 */
private suspend fun handshakeLogin(
    r: ByteReadChannel,
    w: ByteWriteChannel,
    rsaKeyPair: RsaKeyPair?,
    authenticate: (String, CharArray) -> AuthenticationResult,
): PostHandshake? {
    logger.debug { "login: received opcode 14 (INIT); sending server session key" }
    val serverKey = performLoginInit(w)
    return when (val next = r.readByte().toInt() and 0xFF) {
        LoginProt.NEW_LOGIN.opcode, LoginProt.RECONNECT.opcode -> {
            logger.debug { "login: received login block opcode $next" }
            if (rsaKeyPair == null) {
                logger.warn { "rejecting login block: no server RSA keypair loaded" }
                null
            } else {
                performLoginBlock(
                    r,
                    w,
                    serverKey,
                    rsaKeyPair,
                    reconnect = next == LoginProt.RECONNECT.opcode,
                    authenticate = authenticate,
                )
                    ?.let { PostHandshake.GameStage(it) }
            }
        }
        else -> { logger.warn { "unexpected opcode $next after login init; closing connection" }; null }
    }
}

/**
 * Drives [codecs] via [ProtocolStage] for the rest of a JS5 connection's life, once
 * [handshakeJs5] has completed successfully. Deliberately unbounded by [HANDSHAKE_TIMEOUT] — an
 * asset-download session can legitimately outlast that short deadline. [Js5RequestHandler] comes
 * from [koin] (see [emu.gateway.js5.installJs5Handlers]); [cipher] is per-connection mutable state,
 * so [emu.gateway.js5.handler.Js5ControlHandler] is still built directly from it, not via Koin.
 */
private suspend fun runJs5Pipeline(r: ByteReadChannel, w: ByteWriteChannel, codecs: CodecRepository, koin: Koin, cipher: XorStreamCipher) {
    val handlers = HandlerRepositoryBuilder().installJs5Handlers(koin, cipher).build()
    ProtocolStage(
        codecs, handlers, cipher,
        readOpcode = { it.readByte().toInt() and 0xFF },
        readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
        writeOpcode = false,
    ).run(r, w)
}

/**
 * Cache directory: `OSRS_CACHE_DIR` overrides the default relative path `cache-data`. `:gateway:run`
 * sets `workingDir` to the repo root (see `build.gradle.kts`) so the default already resolves there;
 * the env var exists so an `installDist`/`distZip` build — launched from an arbitrary CWD — can be
 * pointed at a cache without relying on its working directory.
 */
private fun cacheDir(): File = File(System.getenv("OSRS_CACHE_DIR") ?: "cache-data")

/** Creates the shared world collision map; individual cache map squares are decoded on demand. */
private fun loadCollisionMap(store: Store): CollisionMap {
    val collision = CacheCollisionMap(
        CacheMapRepository(store),
        CacheObjectDefinitionRepository(store),
    )
    logger.info { "initialized lazy cache-backed world collision" }
    return collision
}

/**
 * `server-rsa.properties` is generated (and gitignored) by `tools:client-patch`;
 * `OSRS_SERVER_RSA_PROPERTIES` overrides the default relative path the same way `OSRS_CACHE_DIR`
 * does for the cache directory.
 */
private fun serverRsaPropertiesFile(): File =
    File(System.getenv("OSRS_SERVER_RSA_PROPERTIES") ?: "server-rsa.properties")

/**
 * Loads the gateway's RSA keypair once at startup (not per connection). A missing/invalid file
 * only disables login (opcodes 16/18) — JS5 still works — so this warns and returns null rather
 * than crashing the whole gateway.
 */
private fun loadServerRsaKeyPair(): RsaKeyPair? = try {
    ServerRsaKeyFile.load(serverRsaPropertiesFile())
} catch (e: Exception) {
    logger.warn {
        "could not load ${serverRsaPropertiesFile().path} (${e.message}) — login (opcodes 16/18) " +
            "will be rejected. Run `./gradlew :tools:client-patch:run` to generate it."
    }
    null
}

/**
 * The immutable JS5 decoder/encoder registry, built once and shared by every connection —
 * unlike the per-connection [XorStreamCipher], it holds no connection state.
 *
 * Assembled by COLLECTING every codec [js5Module] declared into a standalone Koin instance scoped
 * to just that module (CLAUDE.md §5a addendum) — not the shared [main] instance, so this registry
 * never accidentally picks up a login/game encoder. Includes a decoder for every
 * [Js5Prot.CONTROL_OPCODES] entry: the client interleaves these control frames with group requests,
 * and although the gateway ignores their payload (see `emu.gateway.js5.handler.Js5ControlHandler`),
 * the pipeline must still be able to decode and consume them — an unbound opcode drops the socket,
 * which the client reports as `error_game_js5io`.
 */
private fun buildJs5CodecRepository(): CodecRepository = koinApplication { modules(js5Module) }.koin.buildCodecRepository()

/**
 * The immutable game-domain encoder registry, built once and shared by every connection — the
 * same hoist-at-startup convention as [buildJs5CodecRepository], scoped to just [gameModule] so it
 * never picks up a JS5/login codec. [emu.gateway.login.runGameStage] gives it to each connection's
 * isolated outbound writer; the repository itself holds no per-connection state, unlike the ISAAC
 * cipher and bounded mailbox each authenticated game login owns.
 */
private fun buildGameCodecRepository(): CodecRepository = koinApplication { modules(gameModule) }.koin.buildCodecRepository()
