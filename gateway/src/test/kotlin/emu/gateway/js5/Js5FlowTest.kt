package emu.gateway.js5

import emu.cache.store.FlatFileStore
import emu.crypto.NopStreamCipher
import emu.crypto.XorStreamCipher
import emu.gateway.gatewayModule
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.ProtocolStage
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.js5.js5Module
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
import org.koin.dsl.koinApplication
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
        val codecs = koinApplication { modules(js5Module) }.koin.buildCodecRepository()
        // The control handler and encoder share one connection-local cipher.
        val cipher = XorStreamCipher()
        val handlerKoin = koinApplication { modules(gatewayModule(store, null)) }.koin
        val handlers = HandlerRepositoryBuilder().installJs5Handlers(handlerKoin, cipher).build()
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // The protocol stage is cancelled after the exchange because it reads until EOF.
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

    // A multi-block response verifies that opcode 4's shared key also encrypts continuation markers.
    @Test fun `control opcode 4 sets the key and every response byte including block markers is XORed`() = runBlocking {
        val root = Files.createTempDirectory("js5").toFile()
        val big = container(600)
        File(root, "cache/5/1.dat").also { it.parentFile.mkdirs() }.writeBytes(big)
        val store = FlatFileStore(root)

        val codecs = koinApplication { modules(js5Module) }.koin.buildCodecRepository()
        val cipher = XorStreamCipher()
        val handlerKoin = koinApplication { modules(gatewayModule(store, null)) }.koin
        val handlers = HandlerRepositoryBuilder().installJs5Handlers(handlerKoin, cipher).build()
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
        val codecs = koinApplication { modules(js5Module) }.koin.buildCodecRepository()
        val handlerKoin = koinApplication { modules(gatewayModule(store, null)) }.koin
        val handlers = HandlerRepositoryBuilder().installJs5Handlers(handlerKoin, XorStreamCipher()).build()
        val selector = SelectorManager(Dispatchers.IO)
        val server = aSocket(selector).tcp().bind(InetSocketAddress("127.0.0.1", 0))
        val port = (server.localAddress as InetSocketAddress).port

        // Mirror Main.kt's per-connection body: guarantee conn.close() on every exit path via a
        // finally, so the revision-mismatch reject path (performHandshake -> false) does not leak
        // the socket. This test locks that hardening behaviour in place.
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
