package emu.netcore.pipeline

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private data class OutboundSessionTestMessage(val n: Int) : OutgoingMessage
private data class UnregisteredMessage(val n: Int) : OutgoingMessage

private object OutboundSessionTestEncoder : MessageEncoder<OutboundSessionTestMessage> {
    override val prot = Prot(100, 1)
    override val messageType = OutboundSessionTestMessage::class.java
    override fun encode(cipher: StreamCipher, message: OutboundSessionTestMessage): ByteArray =
        byteArrayOf(message.n.toByte())
}

/** Returns each value in [values] in turn from [nextInt], one per call. */
private class OutboundSessionScriptedCipher(private val values: List<Int>) : StreamCipher {
    private var i = 0
    override fun nextInt(): Int = values[i++]
}

/** Verifies registry lookup and ISAAC framing through the per-connection [OutboundSession]. */
class OutboundSessionTest {
    @Test fun `send encodes via the codec registry and ISAAC-adjusts the opcode under a real cipher`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(OutboundSessionTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = OutboundSessionScriptedCipher(listOf(5))
        val session = OutboundSession(codecs, cipher, ch)

        session.send(OutboundSessionTestMessage(42))

        assertEquals((100 + 5) and 0xFF, ch.readByte().toInt() and 0xFF) // opcode ISAAC-adjusted
        assertEquals(42, ch.readByte().toInt() and 0xFF)                 // body untouched
    }

    @Test fun `send with NopStreamCipher is byte-identical to the raw opcode`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(OutboundSessionTestEncoder).build()
        val ch = ByteChannel(true)
        val session = OutboundSession(codecs, NopStreamCipher, ch)

        session.send(OutboundSessionTestMessage(7))

        assertEquals(100, ch.readByte().toInt() and 0xFF)
        assertEquals(7, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `writeOpcode false never consumes a keystream int for the opcode`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(OutboundSessionTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = OutboundSessionScriptedCipher(emptyList()) // throws if any nextInt() call is made
        val session = OutboundSession(codecs, cipher, ch, writeOpcode = false)

        session.send(OutboundSessionTestMessage(9))

        assertEquals(9, ch.readByte().toInt() and 0xFF) // just the body, no opcode byte
    }

    @Test fun `send throws a clear error when no encoder is registered for the message type`(): Unit = runBlocking {
        val codecs = CodecRepositoryBuilder().build()
        val ch = ByteChannel(true)
        val session = OutboundSession(codecs, NopStreamCipher, ch)

        assertFailsWith<IllegalStateException> { session.send(UnregisteredMessage(1)) }
    }

    @Test fun `two sequential sends both apply the ISAAC opcode adjustment in order`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindEncoder(OutboundSessionTestEncoder).build()
        val ch = ByteChannel(true)
        val cipher = OutboundSessionScriptedCipher(listOf(1, 2))
        val session = OutboundSession(codecs, cipher, ch)

        session.send(OutboundSessionTestMessage(10))
        session.send(OutboundSessionTestMessage(20))

        assertEquals(101, ch.readByte().toInt() and 0xFF) // 100 + 1
        assertEquals(10, ch.readByte().toInt() and 0xFF)
        assertEquals(102, ch.readByte().toInt() and 0xFF) // 100 + 2
        assertEquals(20, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `wireSize includes smart opcode and variable length framing without consuming session ISAAC`() = runBlocking {
        val body = ByteArray(300)
        val encoder = object : MessageEncoder<OutboundSessionTestMessage> {
            override val prot = Prot(138, Prot.VAR_SHORT)
            override val messageType = OutboundSessionTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: OutboundSessionTestMessage): ByteArray = body
        }
        val codecs = CodecRepositoryBuilder().bindEncoder(encoder).build()
        val ch = ByteChannel(true)
        val cipher = OutboundSessionScriptedCipher(listOf(5, 9))
        val session = OutboundSession(codecs, cipher, ch)

        assertEquals(2 + 2 + body.size, session.wireSize(OutboundSessionTestMessage(0)))
        session.send(OutboundSessionTestMessage(0))

        // The size probe used NOP, leaving the real session cipher untouched for both smart bytes.
        assertEquals((128 + 5) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals((138 + 9) and 0xFF, ch.readByte().toInt() and 0xFF)
    }
}
