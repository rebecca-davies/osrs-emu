package emu.server.game.network

import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.PacketGroupStart

/** Expands packet groups and publishes a batch with exactly one socket flush. */
internal class GameOutboundWriter(
    private val session: OutboundSession,
) {
    suspend fun write(batch: GameOutputBatch) {
        val messages = buildList {
            for (segment in batch.segments) {
                when (segment) {
                    is GameOutputSegment.Packets -> addAll(segment.messages)
                    is GameOutputSegment.PacketGroup -> {
                        val length = segment.messages.sumOf(session::wireSize)
                        require(length <= Short.MAX_VALUE) { "packet group too large: $length" }
                        add(PacketGroupStart(length))
                        addAll(segment.messages)
                    }
                }
            }
        }
        session.sendBatch(messages)
    }
}
