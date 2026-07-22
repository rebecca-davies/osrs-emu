package emu.transport.pipeline.outbound

import emu.crypto.StreamCipher
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.codec.CodecRepository
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteWriteChannel

/**
 * Per-connection packet writer using runtime message-type lookup. Batch writes preserve order and
 * flush only after the final packet.
 */
class PacketWriter(
    private val codecs: CodecRepository,
    private val cipher: StreamCipher,
    private val write: ByteWriteChannel,
    private val writeOpcode: Boolean = true,
) {
    @Suppress("UNCHECKED_CAST")
    suspend fun send(message: OutgoingMessage) {
        val encoder = encoderFor(message)
        writePacket(write, encoder, message, cipher, writeOpcode)
    }

    /** Writes an ordered batch with one final flush. Callers must serialize access to this writer. */
    suspend fun sendBatch(messages: List<OutgoingMessage>) {
        messages.forEachIndexed { index, message ->
            val encoder = encoderFor(message)
            writePacket(
                write = write,
                encoder = encoder,
                message = message,
                cipher = cipher,
                writeOpcode = writeOpcode,
                flush = index == messages.lastIndex,
            )
        }
    }

    /**
     * Encodes [message] once without advancing the connection cipher. The registered encoder must
     * explicitly declare that its body is cipher-independent.
     */
    fun encodeBody(message: OutgoingMessage): EncodedMessageBody {
        val encoder = encoderFor(message)
        require(encoder is CipherIndependentMessageEncoder<*>) {
            "encoder for ${message.javaClass} does not declare a cipher-independent body"
        }
        @Suppress("UNCHECKED_CAST")
        val body = (encoder as CipherIndependentMessageEncoder<OutgoingMessage>).encode(message)
        return EncodedMessageBody(encoder.prot, body)
    }

    /** Returns the complete framed byte count for an already-encoded body. */
    fun wireSize(body: EncodedMessageBody): Int {
        if (!writeOpcode) return body.bytes.size
        val opcodeSize = if (body.prot.opcode < 128) 1 else 2
        val lengthSize = when (body.prot.size) {
            Prot.VAR_BYTE -> 1
            Prot.VAR_SHORT -> 2
            else -> 0
        }
        return opcodeSize + lengthSize + body.bytes.size
    }

    /** Writes ordered encoded bodies with one final flush and without re-running their codecs. */
    suspend fun sendEncodedBatch(bodies: List<EncodedMessageBody>) {
        bodies.forEachIndexed { index, body ->
            writeEncodedBody(
                write = write,
                prot = body.prot,
                body = body.bytes,
                cipher = cipher,
                writeOpcode = writeOpcode,
                flush = index == bodies.lastIndex,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun encoderFor(message: OutgoingMessage): MessageEncoder<OutgoingMessage> =
        codecs.encoder(message.javaClass) as? MessageEncoder<OutgoingMessage>
            ?: error("no encoder for ${message.javaClass}")
}
