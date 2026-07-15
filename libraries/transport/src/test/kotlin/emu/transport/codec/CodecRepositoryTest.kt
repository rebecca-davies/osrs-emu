package emu.transport.codec

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.message.IncomingMessage
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

private data class Ping(val n: Int) : IncomingMessage
private data class Pong(val n: Int) : OutgoingMessage

private object PingDecoder : MessageDecoder<Ping> {
    override val prot = Prot(7, 1)
    override fun decode(buf: JagexBuffer) = Ping(buf.readUByte())
}
private object ReplacementPingDecoder : MessageDecoder<Ping> {
    override val prot = Prot(7, 1)
    override fun decode(buf: JagexBuffer) = Ping(buf.readUByte() + 1)
}
private object PongEncoder : MessageEncoder<Pong> {
    override val prot = Prot(9, 1)
    override val messageType = Pong::class.java
    override fun encode(cipher: StreamCipher, message: Pong): ByteArray = byteArrayOf(message.n.toByte())
}
private object ReplacementPongEncoder : MessageEncoder<Pong> {
    override val prot = Prot(10, 1)
    override val messageType = Pong::class.java
    override fun encode(cipher: StreamCipher, message: Pong): ByteArray = byteArrayOf((message.n + 1).toByte())
}

class CodecRepositoryTest {
    private val repo = CodecRepositoryBuilder()
        .bindDecoder(PingDecoder)
        .bindEncoder(PongEncoder)
        .build()

    @Test fun `decoder looked up by opcode`() {
        assertSame(PingDecoder, repo.decoder(7))
        assertNull(repo.decoder(123))
    }

    @Test fun `encoder looked up by message class`() {
        assertSame(PongEncoder, repo.encoder(Pong::class.java))
    }

    @Test fun `decoder produces the message`() {
        val msg = repo.decoder(7)!!.decode(JagexBuffer(byteArrayOf(42)))
        assertEquals(Ping(42), msg)
    }

    @Test fun `encoder produces bytes`() {
        @Suppress("UNCHECKED_CAST")
        val bytes = (repo.encoder(Pong::class.java) as MessageEncoder<Pong>).encode(NopStreamCipher, Pong(5))
        assertEquals(5, bytes[0].toInt())
    }

    @Test fun `rejected duplicate decoder leaves the original binding intact`() {
        val builder = CodecRepositoryBuilder().bindDecoder(PingDecoder)

        assertFailsWith<IllegalArgumentException> {
            builder.bindDecoder(ReplacementPingDecoder)
        }

        assertSame(PingDecoder, builder.build().decoder(PingDecoder.prot.opcode))
    }

    @Test fun `rejected duplicate encoder leaves the original binding intact`() {
        val builder = CodecRepositoryBuilder().bindEncoder(PongEncoder)

        assertFailsWith<IllegalArgumentException> {
            builder.bindEncoder(ReplacementPongEncoder)
        }

        assertSame(PongEncoder, builder.build().encoder(Pong::class.java))
    }
}
