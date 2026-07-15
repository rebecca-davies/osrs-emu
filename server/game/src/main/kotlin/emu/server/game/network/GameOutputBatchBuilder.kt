package emu.server.game.network

import emu.netcore.message.OutgoingMessage

/** Builds one immutable output batch while preserving packet and packet-group order. */
internal class GameOutputBatchBuilder {
    private val segments = mutableListOf<GameOutputSegment>()

    fun packet(message: OutgoingMessage) {
        packets(listOf(message))
    }

    fun packets(messages: List<OutgoingMessage>) {
        if (messages.isNotEmpty()) segments += GameOutputSegment.Packets(messages.toList())
    }

    fun packetGroup(messages: List<OutgoingMessage>) {
        require(messages.isNotEmpty()) { "packet group must not be empty" }
        segments += GameOutputSegment.PacketGroup(messages.toList())
    }

    fun build() = GameOutputBatch(segments.toList())
}
