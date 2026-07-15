package emu.transport.pipeline

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.transport.codec.CodecRepository
import emu.transport.codec.MessageEncoder
import emu.transport.message.OutgoingMessage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteWriteChannel

/**
 * Per-connection packet writer using runtime message-type lookup. [sendBatch] preserves order and
 * flushes only after the final packet.
 */
class OutboundSession(
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

    /** Writes an ordered batch with one final flush. Callers must serialize access to this session. */
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
     * Returns the complete framed byte count [send] will write for [message], without advancing the
     * connection's [cipher]. This is used to declare rev-239 `PACKET_GROUP_START` lengths before
     * sending the group's members. It encodes the body once with [NopStreamCipher] solely to measure
     * it, so callers must use it only with body encoders whose output length is cipher-independent
     * (all game encoders satisfy that contract; JS5's cipher-dependent payload encoder does not).
     */
    fun wireSize(message: OutgoingMessage): Int {
        val encoder = encoderFor(message)
        val bodySize = encoder.encode(NopStreamCipher, message).size
        if (!writeOpcode) return bodySize
        val opcodeSize = if (encoder.prot.opcode < 128) 1 else 2
        val lengthSize = when (encoder.prot.size) {
            Prot.VAR_BYTE -> 1
            Prot.VAR_SHORT -> 2
            else -> 0
        }
        return opcodeSize + lengthSize + bodySize
    }

    @Suppress("UNCHECKED_CAST")
    private fun encoderFor(message: OutgoingMessage): MessageEncoder<OutgoingMessage> =
        codecs.encoder(message.javaClass) as? MessageEncoder<OutgoingMessage>
            ?: error("no encoder for ${message.javaClass}")
}
