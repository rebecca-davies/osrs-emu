package emu.netcore.pipeline

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.codec.CodecRepositoryBuilder
import emu.netcore.codec.MessageDecoder
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
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
    override fun encode(cipher: StreamCipher, message: Reply): ByteArray = byteArrayOf(message.n.toByte())
}

class ProtocolStageTest {
    @Test fun `stage decodes, handles, and encodes a reply`() = runBlocking {
        val codecs = CodecRepositoryBuilder().bindDecoder(AskDecoder).bindEncoder(ReplyEncoder).build()
        // handler doubles the number
        val handler = MessageHandler<IncomingMessage> { msg, out ->
            val ask = msg as Ask
            out(Reply(ask.n * 2))
        }
        val stage = ProtocolStage(
            codecs, handler, NopStreamCipher,
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
}
