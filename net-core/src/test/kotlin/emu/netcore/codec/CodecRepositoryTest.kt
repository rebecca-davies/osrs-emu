package emu.netcore.codec

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private data class Ping(val n: Int) : IncomingMessage
private data class Pong(val n: Int) : OutgoingMessage

private object PingDecoder : MessageDecoder<Ping> {
    override val prot = Prot(7, 1)
    override fun decode(buf: JagexBuffer) = Ping(buf.readUByte())
}
private object PongEncoder : MessageEncoder<Pong> {
    override val prot = Prot(9, 1)
    override val messageType = Pong::class.java
    override fun encode(cipher: StreamCipher, message: Pong): ByteArray = byteArrayOf(message.n.toByte())
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
}
