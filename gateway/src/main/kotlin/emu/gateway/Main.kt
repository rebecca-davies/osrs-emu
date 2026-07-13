package emu.gateway

import emu.cache.store.FlatFileStore
import emu.crypto.NopStreamCipher
import emu.gateway.js5.Js5Handler
import emu.gateway.js5.performHandshake
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
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

private const val JS5_OPCODE = 15

fun main() = runBlocking {
    val store = FlatFileStore(File("cache-data"))
    val codecs = CodecRepositoryBuilder()
        .bindDecoder(Js5RequestDecoder(prefetch = false))
        .bindDecoder(Js5RequestDecoder(prefetch = true))
        .bindEncoder(Js5ResponseEncoder)
        .build()
    val selector = SelectorManager(Dispatchers.IO)
    val server = aSocket(selector).tcp().bind(InetSocketAddress("0.0.0.0", 43594))
    println("gateway listening on 43594")
    while (true) {
        val conn = server.accept()
        launch {
            try {
                val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
                when (r.readByte().toInt() and 0xFF) {
                    JS5_OPCODE -> if (performHandshake(r, w)) {
                        ProtocolStage(
                            codecs, Js5Handler(store), NopStreamCipher,
                            readOpcode = { it.readByte().toInt() and 0xFF },
                            readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                            writeOpcode = false,
                        ).run(r, w)
                    }
                    else -> conn.close()   // login opcode 14 etc. handled in a later plan
                }
            } catch (_: Throwable) { conn.close() }
        }
    }
}
