package emu.gateway.js5

import emu.cache.store.FlatFileStore
import emu.crypto.NopStreamCipher
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.message.IncomingMessage
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs235.js5.Js5GroupResponse
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
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class Js5FlowTest {
    private fun store(): FlatFileStore {
        val root = Files.createTempDirectory("js5").toFile()
        val f = File(root, "cache/255/255.dat"); f.parentFile.mkdirs()
        f.writeBytes(byteArrayOf(0, 0, 0, 0, 3, 9, 8, 7))
        return FlatFileStore(root)
    }

    @Test fun `handshake then pipeline serves the group`() = runBlocking {
        val store = store()
        val codecs = CodecRepositoryBuilder()
            .bindDecoder(Js5RequestDecoder(prefetch = false))
            .bindDecoder(Js5RequestDecoder(prefetch = true))
            .bindEncoder(Js5ResponseEncoder)
            .build()
        val handler = Js5Handler(store)
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // ProtocolStage.run() loops reading the next opcode until the connection closes, so this
        // job never completes on its own. It is cancelled below once the exchange under test has
        // happened; otherwise closing the client races the server's next readOpcode() call, which
        // throws EOFException instead of returning cleanly, failing runBlocking's structured
        // concurrency even though every assertion already passed.
        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            r.readByte() // consume opcode 15
            if (performHandshake(r, w)) {
                ProtocolStage(
                    codecs, handler, NopStreamCipher,
                    readOpcode = { it.readByte().toInt() and 0xFF },
                    readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                    writeOpcode = false,
                ).run(r, w)
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(15)
        val hs = ByteArray(20); hs[3] = 235.toByte(); cw.writeFully(hs)
        assertEquals(0, cr.readByte().toInt() and 0xFF)                 // handshake ok
        cw.writeFully(byteArrayOf(1, 255.toByte(), 0, 255.toByte()))    // request (255,255) urgent
        val expected = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(255, 255, byteArrayOf(0,0,0,0,3,9,8,7), false))
        val got = ByteArray(expected.size); cr.readFully(got)
        assertEquals(expected.toList(), got.toList())

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
