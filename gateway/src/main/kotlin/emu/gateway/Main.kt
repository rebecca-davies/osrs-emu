package emu.gateway

import emu.cache.store.FlatFileStore
import emu.crypto.Js5XorCipher
import emu.gateway.js5.Js5Handler
import emu.gateway.js5.performHandshake
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs235.js5.Js5ControlDecoder
import emu.protocol.osrs235.js5.Js5Prot
import emu.protocol.osrs235.js5.Js5RequestDecoder
import emu.protocol.osrs235.js5.Js5ResponseEncoder
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

fun main() = runBlocking {
    val store = FlatFileStore(File("cache-data"))
    val codecs = CodecRepositoryBuilder()
        .bindDecoder(Js5RequestDecoder(prefetch = false))
        .bindDecoder(Js5RequestDecoder(prefetch = true))
        // JS5 control frames the client interleaves with group requests (2=logged in, 3=logged out,
        // 4=xor key, 6=init, 7=keepalive). Consumed and ignored; without them the pipeline drops the
        // socket on the first control opcode -> client reports error_game_js5io.
        .apply { intArrayOf(2, 3, 4, 6, 7).forEach { bindDecoder(Js5ControlDecoder(it)) } }
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
                        val cipher = Js5XorCipher()
                        ProtocolStage(
                            codecs, Js5Handler(store, cipher), cipher,
                            readOpcode = { it.readByte().toInt() and 0xFF },
                            readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                            writeOpcode = false,
                        ).run(r, w)
                    }
                    else -> {}   // login opcode 14 etc. handled in a later plan
                }
            } catch (_: Throwable) {
                // swallow: keep the accept loop alive
            } finally {
                conn.close()
            }
        }
    }
}
