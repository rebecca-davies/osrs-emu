package emu.gateway

import emu.cache.store.FlatFileStore
import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import emu.crypto.XorStreamCipher
import emu.gateway.js5.installJs5Handlers
import emu.gateway.js5.performHandshake
import emu.gateway.login.GAME_IDLE_TIMEOUT
import emu.gateway.login.GameCiphers
import emu.gateway.login.ServerRsaKeyFile
import emu.gateway.login.performLoginBlock
import emu.gateway.login.performLoginInit
import emu.gateway.login.runGameStage
import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs239.game.installGame
import emu.protocol.osrs239.js5.Js5Prot
import emu.protocol.osrs239.js5.installJs5
import emu.protocol.osrs239.login.LoginProt
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
import kotlinx.coroutines.withTimeout
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
 * RSA keypair) is immutable and shared read-only across every connection.
 */
fun main() = runBlocking {
    val store = FlatFileStore(cacheDir())
    val rsaKeyPair = loadServerRsaKeyPair()
    val codecs = buildJs5CodecRepository()
    val gameCodecs = buildGameCodecRepository()

    val selector = SelectorManager(Dispatchers.IO)
    val server = aSocket(selector).tcp().bind(InetSocketAddress("0.0.0.0", 43594))
    logger.info { "gateway listening on 43594" }
    while (true) {
        val conn = server.accept()
        launch { handleConnection(conn, store, codecs, gameCodecs, rsaKeyPair) }
    }
}

/**
 * What a successful pre-game handshake hands off to run *outside* [HANDSHAKE_TIMEOUT]'s deadline:
 * either the JS5 asset-serving pipeline, or the GAME stage of a newly logged-in connection. Kept
 * private — it never appears in an `internal`/public signature (see [handleConnection]).
 */
private sealed interface PostHandshake {
    data class Js5Pipeline(val cipher: XorStreamCipher) : PostHandshake
    data class GameStage(val ciphers: GameCiphers) : PostHandshake
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
 * tiny duration so a timeout path can be exercised without a real-time sleep.
 */
internal suspend fun handleConnection(
    conn: Socket,
    store: Store,
    codecs: CodecRepository,
    gameCodecs: CodecRepository,
    rsaKeyPair: RsaKeyPair?,
    handshakeTimeout: Duration = HANDSHAKE_TIMEOUT,
    gameIdleTimeout: Duration = GAME_IDLE_TIMEOUT,
) {
    try {
        val r = conn.openReadChannel()
        val w = conn.openWriteChannel(autoFlush = false)
        val next = withTimeout(handshakeTimeout) {
            when (val opcode = r.readByte().toInt() and 0xFF) {
                Js5Prot.HANDSHAKE.opcode -> handshakeJs5(r, w)
                LoginProt.INIT.opcode -> handshakeLogin(r, w, rsaKeyPair)
                else -> { logger.warn { "unknown first opcode $opcode; closing connection" }; null }
            }
        }
        when (next) {
            is PostHandshake.Js5Pipeline -> runJs5Pipeline(r, w, store, codecs, next.cipher)
            is PostHandshake.GameStage ->
                runGameStage(r, w, next.ciphers.inbound, next.ciphers.outbound, gameCodecs, gameIdleTimeout)
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
 * [PostHandshake.GameStage] action carrying the resulting [GameCiphers]; returns null (connection
 * left for the caller to close) if no server RSA keypair was loaded at startup, the opcode after
 * INIT is neither [LoginProt.NEW_LOGIN] nor [LoginProt.RECONNECT], or the login block itself fails.
 */
private suspend fun handshakeLogin(r: ByteReadChannel, w: ByteWriteChannel, rsaKeyPair: RsaKeyPair?): PostHandshake? {
    logger.debug { "login: received opcode 14 (INIT); sending server session key" }
    val serverKey = performLoginInit(w)
    return when (val next = r.readByte().toInt() and 0xFF) {
        LoginProt.NEW_LOGIN.opcode, LoginProt.RECONNECT.opcode -> {
            logger.debug { "login: received login block opcode $next" }
            if (rsaKeyPair == null) {
                logger.warn { "rejecting login block: no server RSA keypair loaded" }
                null
            } else {
                performLoginBlock(r, w, serverKey, rsaKeyPair)?.let { PostHandshake.GameStage(it) }
            }
        }
        else -> { logger.warn { "unexpected opcode $next after login init; closing connection" }; null }
    }
}

/**
 * Drives [codecs] via [ProtocolStage] for the rest of a JS5 connection's life, once
 * [handshakeJs5] has completed successfully. Deliberately unbounded by [HANDSHAKE_TIMEOUT] — an
 * asset-download session can legitimately outlast that short deadline.
 */
private suspend fun runJs5Pipeline(r: ByteReadChannel, w: ByteWriteChannel, store: Store, codecs: CodecRepository, cipher: XorStreamCipher) {
    val handlers = HandlerRepositoryBuilder().installJs5Handlers(store, cipher).build()
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
 * Delegates to `installJs5()`, which includes a decoder for every [Js5Prot.CONTROL_OPCODES] entry:
 * the client interleaves these control frames with group requests, and although the gateway ignores
 * their payload (see `emu.gateway.js5.Js5ControlHandler`), the pipeline must still be able to decode
 * and consume them — an unbound opcode drops the socket, which the client reports as
 * `error_game_js5io`.
 */
private fun buildJs5CodecRepository(): CodecRepository = CodecRepositoryBuilder().installJs5().build()

/**
 * The immutable game-domain encoder registry, built once and shared by every connection — the
 * same hoist-at-startup convention as [buildJs5CodecRepository]. [emu.gateway.login.runGameStage]
 * uses it (via [emu.netcore.pipeline.OutboundSession]) to encode the initial-scene packets; it
 * holds no per-connection state, unlike the outbound ISAAC cipher each connection gets from its own
 * [emu.gateway.login.GameCiphers].
 */
private fun buildGameCodecRepository(): CodecRepository = CodecRepositoryBuilder().installGame().build()
