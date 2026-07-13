package emu.gateway

import emu.cache.store.FlatFileStore
import emu.crypto.RsaKeyPair
import emu.crypto.XorStreamCipher
import emu.gateway.js5.Js5Handler
import emu.gateway.js5.performHandshake
import emu.gateway.login.ServerRsaKeyFile
import emu.gateway.login.performLoginBlock
import emu.gateway.login.performLoginInit
import emu.gateway.login.runGameStage
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs239.js5.Js5ControlDecoder
import emu.protocol.osrs239.js5.Js5Prot
import emu.protocol.osrs239.js5.Js5RequestDecoder
import emu.protocol.osrs239.js5.Js5ResponseEncoder
import emu.protocol.osrs239.login.LoginProt
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

// Cache directory: OSRS_CACHE_DIR overrides the default relative path `cache-data`. `:gateway:run`
// sets workingDir to the repo root (see build.gradle.kts) so the default already resolves there;
// the env var exists so an installDist/distZip build — launched from an arbitrary CWD — can be
// pointed at a cache without relying on its working directory.
private fun cacheDir(): File = File(System.getenv("OSRS_CACHE_DIR") ?: "cache-data")

// server-rsa.properties is generated (and gitignored) by tools:client-patch; OSRS_SERVER_RSA_PROPERTIES
// overrides the default relative path the same way OSRS_CACHE_DIR does for the cache directory.
private fun serverRsaPropertiesFile(): File =
    File(System.getenv("OSRS_SERVER_RSA_PROPERTIES") ?: "server-rsa.properties")

fun main() = runBlocking {
    val store = FlatFileStore(cacheDir())
    // Loaded once at startup (not per connection): missing this file only disables login (opcodes
    // 16/18); JS5 still works, so don't crash the whole gateway over it.
    val rsaKeyPair: RsaKeyPair? = try {
        ServerRsaKeyFile.load(serverRsaPropertiesFile())
    } catch (e: Exception) {
        println(
            "WARNING: could not load ${serverRsaPropertiesFile().path} (${e.message}) — login " +
                "(opcodes 16/18) will be rejected. Run `./gradlew :tools:client-patch:run` to generate it.",
        )
        null
    }
    val codecs = CodecRepositoryBuilder()
        .bindDecoder(Js5RequestDecoder(prefetch = false))
        .bindDecoder(Js5RequestDecoder(prefetch = true))
        // JS5 control frames the client interleaves with group requests (see Js5Prot.CONTROL_*).
        // Consumed and ignored; without them the pipeline drops the socket on the first control
        // opcode -> client reports error_game_js5io.
        .apply { Js5Prot.CONTROL_OPCODES.forEach { bindDecoder(Js5ControlDecoder(it)) } }
        .bindEncoder(Js5ResponseEncoder)
        .build()
    val selector = SelectorManager(Dispatchers.IO)
    val server = aSocket(selector).tcp().bind(InetSocketAddress("0.0.0.0", 43594))
    println("gateway listening on 43594")
    while (true) {
        val conn = server.accept()
        launch {
            // Guarantee the socket is closed on EVERY exit path — not just exceptions. A revision
            // mismatch (performHandshake -> false) and an unknown opcode (ProtocolStage.run returns)
            // both complete normally and would otherwise leak the fd. Exceptions are still swallowed
            // so one bad client cannot kill the accept loop.
            try {
                val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
                when (r.readByte().toInt() and 0xFF) {
                    Js5Prot.HANDSHAKE.opcode -> if (performHandshake(r, w)) {
                        // One XOR cipher per connection: the handler sets its key from control
                        // opcode 4, and the same instance obfuscates every response the encoder emits.
                        val cipher = XorStreamCipher()
                        ProtocolStage(
                            codecs, Js5Handler(store, cipher), cipher,
                            readOpcode = { it.readByte().toInt() and 0xFF },
                            readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                            writeOpcode = false,
                        ).run(r, w)
                    }
                    LoginProt.INIT.opcode -> {
                        println("login: received opcode 14 (INIT); sending server session key")
                        val serverKey = performLoginInit(w)
                        when (val next = r.readByte().toInt() and 0xFF) {
                            LoginProt.NEW_LOGIN.opcode, LoginProt.RECONNECT.opcode -> {
                                println("login: received login block opcode $next")
                                if (rsaKeyPair == null) {
                                    println("Rejecting login block: no server RSA keypair loaded.")
                                } else {
                                    val ciphers = performLoginBlock(r, w, serverKey, rsaKeyPair)
                                    if (ciphers != null) runGameStage(r, ciphers.inbound)
                                }
                            }
                            else -> println("Unexpected opcode $next after login init; closing.")
                        }
                    }
                    else -> {}   // unknown first opcode: close
                }
            } catch (t: Throwable) {
                // swallow to keep the accept loop alive, but log so a login/JS5 failure is visible
                // (Task 7 debugging: an exception here otherwise looks like total silence).
                println("connection handler threw ${t.javaClass.name}: ${t.message}")
                t.printStackTrace()
            } finally {
                conn.close()
            }
        }
    }
}
