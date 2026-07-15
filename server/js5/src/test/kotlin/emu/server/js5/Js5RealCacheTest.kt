package emu.server.js5

import emu.server.js5.wire.installJs5Handlers
import emu.server.js5.wire.performHandshake
import emu.cache.store.FlatFileStore
import emu.crypto.XorStreamCipher
import emu.server.js5.handler.Js5RequestHandler
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.transport.pipeline.ProtocolStage
import emu.protocol.osrs239.js5.buildJs5CodecRepository
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
import kotlin.test.Test
import kotlin.test.assertEquals

class Js5RealCacheTest {
    @Test fun `composed pipeline serves the real master index if cache-data exists`() = runBlocking {
        val root = File("../cache-data")
        if (!File(root, "cache/255/255.dat").isFile) {
            println("SKIP: no cache-data — run :tools:cache-fetch:run first"); return@runBlocking
        }
        val codecs = buildJs5CodecRepository()
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port
        val serverJob = launch {
            val conn = server.accept(); val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            r.readByte()
            // Control frames and response encoding share one connection-local cipher.
            val cipher = XorStreamCipher()
            val handlers =
                HandlerRepositoryBuilder()
                    .installJs5Handlers(Js5RequestHandler(FlatFileStore(root)), cipher)
                    .build()
            if (performHandshake(r, w)) ProtocolStage(
                codecs, handlers, cipher,
                readOpcode = { it.readByte().toInt() and 0xFF },
                readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                writeOpcode = false,
            ).run(r, w)
        }
        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(15); val hs = ByteArray(20); hs[3] = 239.toByte(); cw.writeFully(hs)
        assertEquals(0, cr.readByte().toInt() and 0xFF)
        cw.writeFully(byteArrayOf(1, 255.toByte(), 0, 255.toByte()))
        assertEquals(255, cr.readByte().toInt() and 0xFF)  // first response byte = archive id
        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
