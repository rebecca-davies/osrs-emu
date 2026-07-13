package emu.netcore.pipeline

import emu.netcore.message.OutgoingMessage

/**
 * What a [PacketHandler] uses to emit output, without ever touching sockets or codecs directly.
 * [ProtocolStage] supplies the implementation: [write] encodes the message via the
 * [emu.netcore.codec.CodecRepository] and writes the bytes to the channel, honoring the same
 * cipher and opcode-prefix rules the pipeline always has. Kept minimal for now — a natural home
 * for per-connection state (session data, ISAAC cipher access) as handlers need it.
 */
interface HandlerContext {
    suspend fun write(message: OutgoingMessage)
}
