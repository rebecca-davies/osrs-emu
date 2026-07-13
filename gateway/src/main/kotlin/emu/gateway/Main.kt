package emu.gateway

import emu.cache.store.FlatFileStore
import emu.cache.store.Store
import emu.crypto.RsaKeyPair
import emu.crypto.XorStreamCipher
import emu.gateway.js5.Js5Handler
import emu.gateway.js5.performHandshake
import emu.gateway.login.ServerRsaKeyFile
import emu.gateway.login.performLoginBlock
import emu.gateway.login.performLoginInit
import emu.gateway.login.runGameStage
import emu.netcore.codec.CodecRepository
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs239.js5.Js5ControlDecoder
import emu.protocol.osrs239.js5.Js5Prot
import emu.protocol.osrs239.js5.Js5RequestDecoder
import emu.protocol.osrs239.js5.Js5ResponseEncoder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private val logger = KotlinLogging.logger {}

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

    val selector = SelectorManager(Dispatchers.IO)
    val server = aSocket(selector).tcp().bind(InetSocketAddress("0.0.0.0", 43594))
    logger.info { "gateway listening on 43594" }
    while (true) {
        val conn = server.accept()
        launch { handleConnection(conn, store, codecs, rsaKeyPair) }
    }
}

/**
 * Owns one accepted connection end to end: reads the first opcode and routes to the JS5 or login
 * flow, then guarantees the socket is closed on every exit path — success, an unhandled opcode, or
 * an exception — so a misbehaving client can never leak the file descriptor or kill the accept
 * loop in [main].
 */
private suspend fun handleConnection(
    conn: Socket,
    store: Store,
    codecs: CodecRepository,
    rsaKeyPair: RsaKeyPair?,
) {
    try {
        val r = conn.openReadChannel()
        val w = conn.openWriteChannel(autoFlush = false)
        when (val opcode = r.readByte().toInt() and 0xFF) {
            Js5Prot.HANDSHAKE.opcode -> handleJs5(r, w, store, codecs)
            LoginProt.INIT.opcode -> handleLogin(r, w, rsaKeyPair)
            else -> logger.warn { "unknown first opcode $opcode; closing connection" }
        }
    } catch (t: Throwable) {
        // Swallow so one bad client cannot kill the accept loop; still logged so a login/JS5
        // failure is visible instead of looking like total silence.
        logger.error(t) { "connection handler threw" }
    } finally {
        conn.close()
    }
}

/**
 * Runs the JS5 flow for a connection whose first opcode was [Js5Prot.HANDSHAKE.opcode]: performs
 * the revision handshake, and on success drives [codecs] via [ProtocolStage] for the rest of the
 * connection's life. One [XorStreamCipher] is created per connection — the handler sets its key
 * from control opcode 4, and the same instance obfuscates every response the encoder emits.
 */
private suspend fun handleJs5(r: ByteReadChannel, w: ByteWriteChannel, store: Store, codecs: CodecRepository) {
    if (!performHandshake(r, w)) return
    val cipher = XorStreamCipher()
    ProtocolStage(
        codecs, Js5Handler(store, cipher), cipher,
        readOpcode = { it.readByte().toInt() and 0xFF },
        readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
        writeOpcode = false,
    ).run(r, w)
}

/**
 * Runs the login flow for a connection whose first opcode was [LoginProt.INIT.opcode]: sends the
 * server session key, then expects either a new-login or reconnect login block next. Login is
 * rejected (connection left to be closed by the caller) if no server RSA keypair was loaded at
 * startup, or if the opcode after INIT is neither [LoginProt.NEW_LOGIN] nor [LoginProt.RECONNECT].
 */
private suspend fun handleLogin(r: ByteReadChannel, w: ByteWriteChannel, rsaKeyPair: RsaKeyPair?) {
    logger.debug { "login: received opcode 14 (INIT); sending server session key" }
    val serverKey = performLoginInit(w)
    when (val next = r.readByte().toInt() and 0xFF) {
        LoginProt.NEW_LOGIN.opcode, LoginProt.RECONNECT.opcode -> {
            logger.debug { "login: received login block opcode $next" }
            if (rsaKeyPair == null) {
                logger.warn { "rejecting login block: no server RSA keypair loaded" }
            } else {
                val ciphers = performLoginBlock(r, w, serverKey, rsaKeyPair)
                if (ciphers != null) runGameStage(r, ciphers.inbound)
            }
        }
        else -> logger.warn { "unexpected opcode $next after login init; closing connection" }
    }
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
 */
private fun buildJs5CodecRepository(): CodecRepository = CodecRepositoryBuilder()
    .bindDecoder(Js5RequestDecoder(prefetch = false))
    .bindDecoder(Js5RequestDecoder(prefetch = true))
    // JS5 control frames the client interleaves with group requests (see Js5Prot.CONTROL_*).
    // Consumed and ignored; without them the pipeline drops the socket on the first control
    // opcode -> client reports error_game_js5io.
    .apply { Js5Prot.CONTROL_OPCODES.forEach { bindDecoder(Js5ControlDecoder(it)) } }
    .bindEncoder(Js5ResponseEncoder)
    .build()
