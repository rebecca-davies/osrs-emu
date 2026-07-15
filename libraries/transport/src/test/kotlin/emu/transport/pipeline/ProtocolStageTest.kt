package emu.transport.pipeline

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.codec.CodecRepositoryBuilder
import emu.transport.codec.MessageDecoder
import emu.transport.codec.MessageEncoder
import emu.transport.message.IncomingMessage
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

private data class Ask(val n: Int) : IncomingMessage
private data class Reply(val n: Int) : OutgoingMessage

private object AskDecoder : MessageDecoder<Ask> {
    override val prot = Prot(1, 1)
    override fun decode(buf: JagexBuffer) = Ask(buf.readUByte())
}
private object ReplyEncoder : MessageEncoder<Reply> {
    override val prot = Prot(1, 1)
    override val messageType = Reply::class.java
    override fun encode(cipher: StreamCipher, message: Reply): ByteArray = byteArrayOf(message.n.toByte())
}

class ProtocolStageTest {
    @Test fun `stage decodes, handles, and encodes a reply`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindDecoder(AskDecoder).bindEncoder(ReplyEncoder).build()
        // handler doubles the number
        val handlers = HandlerRepositoryBuilder()
            .bind(Ask::class.java) { ask, ctx -> ctx.write(Reply(ask.n * 2)) }
            .build()
        val stage = ProtocolStage(
            codecs, handlers, NopStreamCipher,
            readOpcode = { it.readByte().toInt() and 0xFF },
            readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
        )
        val toStage = ByteChannel(true)     // client -> stage
        val fromStage = ByteChannel(true)   // stage -> client
        // stage.run() loops forever reading the next opcode, so the job never completes on its
        // own; cancel it once the exchange under test has happened, otherwise runBlocking (which
        // awaits all child coroutines) would hang forever.
        val job = launch { stage.run(toStage, fromStage) }

        toStage.writeByte(1)     // opcode
        toStage.writeByte(21)    // payload: n=21
        toStage.flush()

        assertEquals(1, fromStage.readByte().toInt() and 0xFF)   // reply opcode
        assertEquals(42, fromStage.readByte().toInt() and 0xFF)  // 21*2

        job.cancel()
    }

    @Test fun `stage can frame and discard a known protocol packet without a decoder`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindDecoder(AskDecoder).bindEncoder(ReplyEncoder).build()
        val handlers = HandlerRepositoryBuilder()
            .bind(Ask::class.java) { ask, ctx -> ctx.write(Reply(ask.n * 2)) }
            .build()
        val protocol = mapOf(1 to AskDecoder.prot, 2 to Prot(2, 2))
        val stage = ProtocolStage(
            codecs, handlers, NopStreamCipher,
            readOpcode = { it.readByte().toInt() and 0xFF },
            readPayload = { ch, prot -> ByteArray(prot.size).also { ch.readFully(it) } },
            findProt = protocol::get,
        )
        val toStage = ByteChannel(true)
        val fromStage = ByteChannel(true)
        val job = launch { stage.run(toStage, fromStage) }

        toStage.writeByte(2)
        toStage.writeByte(0xAA.toByte())
        toStage.writeByte(0xBB.toByte())
        toStage.writeByte(1)
        toStage.writeByte(21)
        toStage.flush()

        assertEquals(1, fromStage.readByte().toInt() and 0xFF)
        assertEquals(42, fromStage.readByte().toInt() and 0xFF)

        job.cancel()
    }
}
