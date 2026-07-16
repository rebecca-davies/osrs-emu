package emu.server.game.network.output

import emu.protocol.osrs239.game.message.cycle.PacketGroupStart
import emu.transport.pipeline.outbound.PacketWriter

/** Expands packet groups and publishes a batch with exactly one socket flush. */
internal class GameOutboundWriter(
    private val packets: PacketWriter,
) {
    suspend fun write(batch: GameOutputBatch) {
        val messages = buildList {
            for (segment in batch.segments) {
                when (segment) {
                    is GameOutputSegment.Packets -> addAll(segment.messages)
                    is GameOutputSegment.PacketGroup -> {
                        val length = segment.messages.sumOf(packets::wireSize)
                        require(length <= Short.MAX_VALUE) { "packet group too large: $length" }
                        add(PacketGroupStart(length))
                        addAll(segment.messages)
                    }
                }
            }
        }
        packets.sendBatch(messages)
    }
}
