package emu.server.js5

import emu.server.js5.wire.installJs5Handlers
import emu.server.js5.wire.performHandshake
import emu.cache.store.FlatFileStore
import emu.crypto.NopStreamCipher
import emu.crypto.XorStreamCipher
import emu.server.js5.handler.Js5RequestHandler
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.transport.pipeline.ProtocolStage
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.js5.codec.Js5ResponseEncoder
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
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Js5FlowTest {
    private fun store(): FlatFileStore {
        val root = Files.createTempDirectory("js5").toFile()
        val f = File(root, "cache/255/255.dat"); f.parentFile.mkdirs()
        f.writeBytes(byteArrayOf(0, 0, 0, 0, 3, 9, 8, 7))
        return FlatFileStore(root)
    }

    // A stored container: [compression=0][u32 dataLen][data...], matching the layout
    // Js5ResponseEncoderTest exercises directly. dataLen=600 forces a multi-block (>512 byte)
    // response so the 0xFF block-marker path is covered together with XOR.
    private fun container(dataLen: Int): ByteArray {
        val out = ByteArray(1 + 4 + dataLen)
        out[1] = (dataLen ushr 24).toByte(); out[2] = (dataLen ushr 16).toByte()
        out[3] = (dataLen ushr 8).toByte(); out[4] = dataLen.toByte()
        for (i in 0 until dataLen) out[5 + i] = (i % 256).toByte()
        return out
    }

    @Test fun `handshake then pipeline serves the group`() = runBlocking {
        val store = store()
        val codecs = buildJs5CodecRepository()
        // The control handler and protocol stage share one connection-local cipher.
        val cipher = XorStreamCipher()
        val handlers = HandlerRepositoryBuilder().installJs5Handlers(Js5RequestHandler(store), cipher).build()
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
                    codecs, handlers, cipher,
                    readOpcode = { it.readByte().toInt() and 0xFF },
                    readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                    writeOpcode = false,
                ).run(r, w)
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(15)
        val hs = ByteArray(20); hs[3] = 239.toByte(); cw.writeFully(hs)
        assertEquals(0, cr.readByte().toInt() and 0xFF)                 // handshake ok
        cw.writeFully(byteArrayOf(1, 255.toByte(), 0, 255.toByte()))    // request (255,255) urgent
        val expected = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(255, 255, byteArrayOf(0,0,0,0,3,9,8,7), false))
        val got = ByteArray(expected.size); cr.readFully(got)
        assertEquals(expected.toList(), got.toList())

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }

    // The control handler and encoder share one connection-local cipher. A response larger than
    // 512 bytes also proves that inserted block markers are transformed with the payload.
    @Test fun `control opcode 4 sets the key and every response byte including block markers is XORed`() = runBlocking {
        val root = Files.createTempDirectory("js5").toFile()
        val big = container(600)
        File(root, "cache/5/1.dat").also { it.parentFile.mkdirs() }.writeBytes(big)
        val store = FlatFileStore(root)

        val codecs = buildJs5CodecRepository()
        val cipher = XorStreamCipher()
        val handlers = HandlerRepositoryBuilder().installJs5Handlers(Js5RequestHandler(store), cipher).build()
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        val serverJob = launch {
            val conn = server.accept()
            val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
            r.readByte()
            if (performHandshake(r, w)) {
                ProtocolStage(
                    codecs, handlers, cipher,
                    readOpcode = { it.readByte().toInt() and 0xFF },
                    readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                    writeOpcode = false,
                ).run(r, w)
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(15)
        val hs = ByteArray(20); hs[3] = 239.toByte(); cw.writeFully(hs)
        assertEquals(0, cr.readByte().toInt() and 0xFF)

        val key = 0x7F
        // Control opcode 4: [opcode][b0=key][b1][b2]. b1/b2 are unused reserved bytes on the wire.
        cw.writeFully(byteArrayOf(Js5Prot.CONTROL_XOR_KEY.toByte(), key.toByte(), 0, 0))
        cw.writeFully(byteArrayOf(1, 5, 0, 1)) // urgent request archive=5, group=1

        val expectedPlain = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(5, 1, big, false))
        assertEquals(609, expectedPlain.size) // sanity: forces the >512 multi-block 0xFF-marker path
        val got = ByteArray(expectedPlain.size); cr.readFully(got)

        // Every byte on the wire — including the inserted 0xFF block markers — must be the plaintext
        // byte XORed with the key the client set via control opcode 4.
        for (i in expectedPlain.indices) {
            assertEquals((expectedPlain[i].toInt() xor key).toByte(), got[i], "byte $i")
        }
        // And decrypting reproduces the encoder's plaintext output exactly, proving the key that
        // reached the encoder was the SAME key the handler set from the control frame.
        val decrypted = ByteArray(got.size) { (got[it].toInt() xor key).toByte() }
        assertEquals(expectedPlain.toList(), decrypted.toList())

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }

    @Test fun `revision mismatch replies 6 and closes the connection`() = runBlocking {
        val store = store()
        val codecs = buildJs5CodecRepository()
        val handlers =
            HandlerRepositoryBuilder()
                .installJs5Handlers(Js5RequestHandler(store), XorStreamCipher())
                .build()
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // The rejected handshake must still close its connection on exit.
        val serverJob = launch {
            val conn = server.accept()
            try {
                val r = conn.openReadChannel(); val w = conn.openWriteChannel(autoFlush = false)
                r.readByte() // consume opcode 15
                if (performHandshake(r, w)) {
                    ProtocolStage(
                        codecs, handlers, NopStreamCipher,
                        readOpcode = { it.readByte().toInt() and 0xFF },
                        readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
                        writeOpcode = false,
                    ).run(r, w)
                }
            } catch (_: Throwable) {
            } finally {
                conn.close()
            }
        }

        val client = aSocket(selector).tcp().connect(InetSocketAddress("127.0.0.1", port))
        val cr = client.openReadChannel(); val cw = client.openWriteChannel(autoFlush = true)
        cw.writeByte(15)
        val hs = ByteArray(20); hs[3] = 234.toByte(); cw.writeFully(hs)   // WRONG revision
        assertEquals(6, cr.readByte().toInt() and 0xFF)                   // out-of-date reply

        // The server closed the connection after replying 6; the client's channel reaches EOF.
        // readByte() throws (or the channel is at end) rather than hanging forever.
        assertFailsWith<Throwable> {
            withTimeout(2000) { cr.readByte() }
        }

        serverJob.cancel()
        client.close(); server.close(); selector.close()
    }
}
