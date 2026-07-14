package emu.netcore.pipeline

import emu.crypto.StreamCipher
import emu.netcore.codec.CodecRepository
import emu.netcore.codec.MessageEncoder
import emu.netcore.message.OutgoingMessage
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
        val encoder = codecs.encoder(message.javaClass) as? MessageEncoder<OutgoingMessage>
            ?: error("no encoder for ${message.javaClass}")
        writePacket(write, encoder, message, cipher, writeOpcode)
    }
}
