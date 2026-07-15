package emu.transport.pipeline

import emu.buffer.JagexBuffer
import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.codec.CodecRepository
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/**
 * Frames, decodes, and dispatches inbound packets using injected protocol framing functions.
 * Unknown opcodes end the stage. Known packets without a decoder are framed and discarded so the
 * stream remains aligned.
 */
class ProtocolStage(
    private val codecs: CodecRepository,
    private val handlers: HandlerRepository,
    private val cipher: StreamCipher = NopStreamCipher,
    private val readOpcode: suspend (ByteReadChannel) -> Int,
    private val readPayload: suspend (ByteReadChannel, Prot) -> ByteArray,
    private val writeOpcode: Boolean = true,
    private val findProt: (Int) -> Prot? = { codecs.decoder(it)?.prot },
) {
    suspend fun run(read: ByteReadChannel, write: ByteWriteChannel) {
        run(read) { message -> emit(message, write) }
    }

    /** Runs with an injected output boundary instead of writing directly to a channel. */
    suspend fun run(read: ByteReadChannel, emit: suspend (OutgoingMessage) -> Unit) {
        val ctx = object : HandlerContext {
            override suspend fun write(message: OutgoingMessage) = emit(message)
        }
        while (true) {
            val opcode = readOpcode(read)
            val prot = findProt(opcode) ?: return
            val payload = readPayload(read, prot)
            val decoder = codecs.decoder(opcode) ?: continue
            val message = decoder.decode(JagexBuffer(payload))
            handlers.dispatch(message, ctx)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun emit(message: OutgoingMessage, write: ByteWriteChannel) {
        val encoder = codecs.encoder(message.javaClass) as? MessageEncoder<OutgoingMessage>
            ?: error("no encoder for ${message.javaClass}")
        writePacket(write, encoder, message, cipher, writeOpcode)
    }
}
