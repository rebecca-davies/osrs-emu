package emu.netcore.pipeline

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private data class OutboundWriterTestMessage(val n: Int) : OutgoingMessage

private object OutboundWriterTestEncoder : MessageEncoder<OutboundWriterTestMessage> {
    override val prot = Prot(200, 1)
    override val messageType = OutboundWriterTestMessage::class.java
    override fun encode(cipher: StreamCipher, message: OutboundWriterTestMessage): ByteArray =
        byteArrayOf(message.n.toByte())
}

/** Returns each value in [values] in turn from [nextInt], one per call. */
private class ScriptedCipher(private val values: List<Int>) : StreamCipher {
    private var i = 0
    override fun nextInt(): Int = values[i++]
}

class OutboundWriterTest {
    @Test fun `writeOpcode true with NopStreamCipher is byte-identical to the raw opcode`() = runBlocking {
        val ch = ByteChannel(true)
        writePacket(ch, OutboundWriterTestEncoder, OutboundWriterTestMessage(42), NopStreamCipher, writeOpcode = true)

        assertEquals(200, ch.readByte().toInt() and 0xFF) // opcode unchanged: +0
        assertEquals(42, ch.readByte().toInt() and 0xFF)  // body untouched
    }

    @Test fun `writeOpcode true ISAAC-adjusts the opcode byte by the cipher's next keystream int`() = runBlocking {
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(5))
        writePacket(ch, OutboundWriterTestEncoder, OutboundWriterTestMessage(42), cipher, writeOpcode = true)

        assertEquals((200 + 5) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals(42, ch.readByte().toInt() and 0xFF) // body still plaintext (encoder ignored cipher)
    }

    @Test fun `opcode adjustment wraps modulo 256`() = runBlocking {
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(200)) // 200 + 200 = 400 -> wraps to 144
        writePacket(ch, OutboundWriterTestEncoder, OutboundWriterTestMessage(1), cipher, writeOpcode = true)

        assertEquals(144, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `writeOpcode false never consumes a keystream int for the opcode (JS5 behavior)`() = runBlocking {
        // Body encoding consumes zero cipher calls for this encoder; a ScriptedCipher with an empty
        // list will throw IndexOutOfBounds if writePacket tries to pull a keystream int anywhere.
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(emptyList())
        writePacket(ch, OutboundWriterTestEncoder, OutboundWriterTestMessage(7), cipher, writeOpcode = false)

        assertEquals(7, ch.readByte().toInt() and 0xFF) // just the body, no opcode byte at all
    }

    @Test fun `body is encoded with the same cipher instance, preserving encoders that XOR their own bytes`() = runBlocking {
        // Mirrors Js5ResponseEncoder: the body encoder consumes the cipher itself (one nextInt() per
        // byte). writePacket must hand the real cipher to encode(), and — because writeOpcode is
        // false here (JS5's wiring) — must not pull any additional keystream int for an opcode byte.
        val xorEncoder = object : MessageEncoder<OutboundWriterTestMessage> {
            override val prot = Prot(200, 1)
            override val messageType = OutboundWriterTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: OutboundWriterTestMessage): ByteArray =
                byteArrayOf((message.n xor (cipher.nextInt() and 0xFF)).toByte())
        }
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(0x2A))
        writePacket(ch, xorEncoder, OutboundWriterTestMessage(7), cipher, writeOpcode = false)

        assertEquals(7 xor 0x2A, ch.readByte().toInt() and 0xFF)
    }
}
