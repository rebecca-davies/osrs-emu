package emu.transport.pipeline.outbound

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.codec.CodecRepositoryBuilder
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

private data class PacketWriterTestMessage(val n: Int) : OutgoingMessage
private data class UnregisteredMessage(val n: Int) : OutgoingMessage

private object PacketWriterTestEncoder : MessageEncoder<PacketWriterTestMessage> {
    override val prot = Prot(100, 1)
    override val messageType = PacketWriterTestMessage::class.java
    override fun encode(cipher: StreamCipher, message: PacketWriterTestMessage): ByteArray =
        byteArrayOf(message.n.toByte())
}

/** Returns each value in [values] in turn from [nextInt], one per call. */
private class PacketWriterScriptedCipher(private val values: List<Int>) : StreamCipher {
    private var i = 0
    override fun nextInt(): Int = values[i++]
}

/** Verifies registry lookup and ISAAC framing through the per-connection [PacketWriter]. */
class PacketWriterTest {
    @Test fun `send encodes via the codec registry and ISAAC-adjusts the opcode under a real cipher`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(PacketWriterTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = PacketWriterScriptedCipher(listOf(5))
        val session = PacketWriter(codecs, cipher, ch)

        session.send(PacketWriterTestMessage(42))

        assertEquals((100 + 5) and 0xFF, ch.readByte().toInt() and 0xFF) // opcode ISAAC-adjusted
        assertEquals(42, ch.readByte().toInt() and 0xFF)                 // body untouched
    }

    @Test fun `send with NopStreamCipher is byte-identical to the raw opcode`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(PacketWriterTestEncoder).build()
        val ch = ByteChannel(true)
        val session = PacketWriter(codecs, NopStreamCipher, ch)

        session.send(PacketWriterTestMessage(7))

        assertEquals(100, ch.readByte().toInt() and 0xFF)
        assertEquals(7, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `writeOpcode false never consumes a keystream int for the opcode`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(PacketWriterTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = PacketWriterScriptedCipher(emptyList()) // throws if any nextInt() call is made
        val session = PacketWriter(codecs, cipher, ch, writeOpcode = false)

        session.send(PacketWriterTestMessage(9))

        assertEquals(9, ch.readByte().toInt() and 0xFF) // just the body, no opcode byte
    }

    @Test fun `send throws a clear error when no encoder is registered for the message type`(): Unit = runBlocking {
        val codecs = CodecRepositoryBuilder().build()
        val ch = ByteChannel(true)
        val session = PacketWriter(codecs, NopStreamCipher, ch)

        assertFailsWith<IllegalStateException> { session.send(UnregisteredMessage(1)) }
    }

    @Test fun `two sequential sends both apply the ISAAC opcode adjustment in order`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(PacketWriterTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = PacketWriterScriptedCipher(listOf(1, 2))
        val session = PacketWriter(codecs, cipher, ch)

        session.send(PacketWriterTestMessage(10))
        session.send(PacketWriterTestMessage(20))

        assertEquals(101, ch.readByte().toInt() and 0xFF) // 100 + 1
        assertEquals(10, ch.readByte().toInt() and 0xFF)
        assertEquals(102, ch.readByte().toInt() and 0xFF) // 100 + 2
        assertEquals(20, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `wireSize includes smart opcode and variable length framing without consuming session ISAAC`() = runBlocking {
        val body = ByteArray(300)
        val encoder = object : MessageEncoder<PacketWriterTestMessage> {
            override val prot = Prot(138, Prot.VAR_SHORT)
            override val messageType = PacketWriterTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: PacketWriterTestMessage): ByteArray = body
        }
        val codecs = CodecRepositoryBuilder().bindEncoder(encoder).build()
        val ch = ByteChannel(true)
        val cipher = PacketWriterScriptedCipher(listOf(5, 9))
        val session = PacketWriter(codecs, cipher, ch)

        assertEquals(2 + 2 + body.size, session.wireSize(PacketWriterTestMessage(0)))
        session.send(PacketWriterTestMessage(0))

        // The size probe used NOP, leaving the real session cipher untouched for both smart bytes.
        assertEquals((128 + 5) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals((138 + 9) and 0xFF, ch.readByte().toInt() and 0xFF)
    }
}
