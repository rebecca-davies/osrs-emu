package emu.transport.pipeline.outbound

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

private data class WritePacketTestMessage(val n: Int) : OutgoingMessage

private object WritePacketTestEncoder : MessageEncoder<WritePacketTestMessage> {
    override val prot = Prot(100, 1)
    override val messageType = WritePacketTestMessage::class.java
    override fun encode(cipher: StreamCipher, message: WritePacketTestMessage): ByteArray =
        byteArrayOf(message.n.toByte())
}

/** Returns each value in [values] in turn from [nextInt], one per call. */
private class ScriptedCipher(private val values: List<Int>) : StreamCipher {
    private var i = 0
    override fun nextInt(): Int = values[i++]
}

class WritePacketTest {
    @Test fun `VAR_BYTE rejects an oversized body before writing a frame`() = runBlocking {
        val encoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(30, Prot.VAR_BYTE)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage) = ByteArray(256)
        }
        val ch = ByteChannel(true)

        assertFailsWith<IllegalArgumentException> {
            writePacket(ch, encoder, WritePacketTestMessage(0), NopStreamCipher, writeOpcode = true)
        }
    }

    @Test fun `VAR_SHORT rejects an oversized body before writing a frame`() = runBlocking {
        val encoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(49, Prot.VAR_SHORT)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage) = ByteArray(65_536)
        }
        val ch = ByteChannel(true)

        assertFailsWith<IllegalArgumentException> {
            writePacket(ch, encoder, WritePacketTestMessage(0), NopStreamCipher, writeOpcode = true)
        }
    }

    @Test fun `writeOpcode true with NopStreamCipher is byte-identical to the raw opcode`() = runBlocking {
        val ch = ByteChannel(true)
        writePacket(ch, WritePacketTestEncoder, WritePacketTestMessage(42), NopStreamCipher, writeOpcode = true)

        assertEquals(100, ch.readByte().toInt() and 0xFF) // opcode unchanged: +0
        assertEquals(42, ch.readByte().toInt() and 0xFF)  // body untouched
    }

    @Test fun `writeOpcode true ISAAC-adjusts the opcode byte by the cipher's next keystream int`() = runBlocking {
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(5))
        writePacket(ch, WritePacketTestEncoder, WritePacketTestMessage(42), cipher, writeOpcode = true)

        assertEquals((100 + 5) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals(42, ch.readByte().toInt() and 0xFF) // body still plaintext (encoder ignored cipher)
    }

    @Test fun `opcode adjustment wraps modulo 256`() = runBlocking {
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(200)) // 100 + 200 = 300 -> wraps to 44
        writePacket(ch, WritePacketTestEncoder, WritePacketTestMessage(1), cipher, writeOpcode = true)

        assertEquals(44, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `writeOpcode false never consumes a keystream int for the opcode (JS5 behavior)`() = runBlocking {
        // Body encoding consumes zero cipher calls for this encoder; a ScriptedCipher with an empty
        // list will throw IndexOutOfBounds if writePacket tries to pull a keystream int anywhere.
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(emptyList())
        writePacket(ch, WritePacketTestEncoder, WritePacketTestMessage(7), cipher, writeOpcode = false)

        assertEquals(7, ch.readByte().toInt() and 0xFF) // just the body, no opcode byte at all
    }

    @Test fun `a VAR_SHORT prot emits opcode then a plaintext u16 big-endian length then the body`() = runBlocking {
        // A 300-byte body forces a two-byte length (0x012C) so both length bytes are exercised.
        val bodyBytes = ByteArray(300) { (it and 0xFF).toByte() }
        val encoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(49, Prot.VAR_SHORT)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage): ByteArray = bodyBytes
        }
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(5)) // one draw for the opcode; length is never ISAAC'd
        writePacket(ch, encoder, WritePacketTestMessage(0), cipher, writeOpcode = true)

        assertEquals((49 + 5) and 0xFF, ch.readByte().toInt() and 0xFF) // ISAAC-adjusted opcode
        assertEquals(0x01, ch.readByte().toInt() and 0xFF) // length hi (300 ushr 8)
        assertEquals(0x2C, ch.readByte().toInt() and 0xFF) // length lo (300 and 0xFF)
        val readBody = ByteArray(300)
        ch.readFully(readBody)
        assertEquals(bodyBytes.toList(), readBody.toList())
    }

    @Test fun `a VAR_BYTE prot emits opcode then a plaintext u8 length then the body`() = runBlocking {
        val bodyBytes = ByteArray(7) { (it + 1).toByte() }
        val encoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(30, Prot.VAR_BYTE)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage): ByteArray = bodyBytes
        }
        val ch = ByteChannel(true)
        writePacket(ch, encoder, WritePacketTestMessage(0), NopStreamCipher, writeOpcode = true)

        assertEquals(30, ch.readByte().toInt() and 0xFF) // opcode (+0 under Nop)
        assertEquals(7, ch.readByte().toInt() and 0xFF) // single-byte length
        val readBody = ByteArray(7)
        ch.readFully(readBody)
        assertEquals(bodyBytes.toList(), readBody.toList())
    }

    @Test fun `a fixed-size prot emits opcode then the body with no length prefix`() = runBlocking {
        // WritePacketTestEncoder has a fixed size (Prot(100, 1)); no length must appear.
        val ch = ByteChannel(true)
        writePacket(ch, WritePacketTestEncoder, WritePacketTestMessage(42), NopStreamCipher, writeOpcode = true)

        assertEquals(100, ch.readByte().toInt() and 0xFF) // opcode
        assertEquals(42, ch.readByte().toInt() and 0xFF) // body immediately follows, no length
    }

    @Test fun `opcode 128 or greater emits a two-byte ISAAC smart and consumes two values`() = runBlocking {
        val encoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(138, 1)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage): ByteArray =
                byteArrayOf(message.n.toByte())
        }
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(5, 9))

        writePacket(ch, encoder, WritePacketTestMessage(42), cipher, writeOpcode = true)

        // pSmart1or2(138) = [128, 138], with one ISAAC value added to each encoded byte.
        assertEquals((128 + 5) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals((138 + 9) and 0xFF, ch.readByte().toInt() and 0xFF)
        assertEquals(42, ch.readByte().toInt() and 0xFF)
    }

    @Test fun `body is encoded with the same cipher instance, preserving encoders that XOR their own bytes`() = runBlocking {
        // Mirrors Js5ResponseEncoder: the body encoder consumes the cipher itself (one nextInt() per
        // byte). writePacket must hand the real cipher to encode(), and — because writeOpcode is
        // false here (JS5's wiring) — must not pull any additional keystream int for an opcode byte.
        val xorEncoder = object : MessageEncoder<WritePacketTestMessage> {
            override val prot = Prot(200, 1)
            override val messageType = WritePacketTestMessage::class.java
            override fun encode(cipher: StreamCipher, message: WritePacketTestMessage): ByteArray =
                byteArrayOf((message.n xor (cipher.nextInt() and 0xFF)).toByte())
        }
        val ch = ByteChannel(true)
        val cipher = ScriptedCipher(listOf(0x2A))
        writePacket(ch, xorEncoder, WritePacketTestMessage(7), cipher, writeOpcode = false)

        assertEquals(7 xor 0x2A, ch.readByte().toInt() and 0xFF)
    }
}
