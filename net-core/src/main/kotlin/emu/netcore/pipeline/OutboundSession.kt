package emu.netcore.pipeline

import emu.crypto.NopStreamCipher
import emu.crypto.StreamCipher
import emu.netcore.codec.CodecRepository
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
import emu.netcore.prot.Prot
import io.ktor.utils.io.ByteWriteChannel

/**
 * A small, reusable per-connection outbound wrapper around [writePacket]: looks the encoder up in
 * [codecs] by the message's runtime class, then writes it through the single shared write path —
 * so every caller (the post-login game stage today, the tick-loop's outbound queue later) reuses
 * the exact same registry lookup + ISAAC-opcode-adjustment contract that [ProtocolStage.emit]
 * already relies on for in-loop replies, instead of re-deriving it per call site.
 *
 * Deliberately holds no other state: [codecs] is shared/immutable across connections, [cipher] and
 * [write] are per-connection, and [writeOpcode] mirrors the protocol's own convention (game packets
 * true, JS5 false — see [writePacket]'s keystream-ordering doc).
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
