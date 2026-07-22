package emu.server.game.network.output

import emu.protocol.osrs239.game.message.cycle.PacketGroupStart
import emu.transport.pipeline.outbound.PacketWriter

/** Expands packet groups and publishes a batch with exactly one socket flush. */
internal class GameOutboundWriter(
    private val packets: PacketWriter,
) {
    suspend fun write(batch: GameOutputBatch) {
        val bodies = buildList {
            for (segment in batch.segments) {
                when (segment) {
                    is GameOutputSegment.Packets -> segment.messages.mapTo(this, packets::encodeBody)
                    is GameOutputSegment.PacketGroup -> {
                        val group = segment.messages.map(packets::encodeBody)
                        val length = group.sumOf(packets::wireSize)
                        require(length <= Short.MAX_VALUE) { "packet group too large: $length" }
                        add(packets.encodeBody(PacketGroupStart(length)))
                        addAll(group)
                    }
                }
            }
        }
        packets.sendEncodedBatch(bodies)
    }
}
