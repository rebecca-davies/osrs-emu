package emu.server.game.network.output

import emu.transport.message.OutgoingMessage

/** One cycle's indivisible, ordered server-to-client output. */
internal data class GameOutputBatch(
    val segments: List<GameOutputSegment>,
) {
    init {
        require(segments.isNotEmpty()) { "game output batch must not be empty" }
    }

    companion object {
        fun packet(message: OutgoingMessage) =
            GameOutputBatch(listOf(GameOutputSegment.Packets(listOf(message))))

        fun build(block: GameOutputBatchBuilder.() -> Unit): GameOutputBatch =
            GameOutputBatchBuilder().apply(block).build()
    }
}

/** A plain packet run or a rev-239 atomic packet group within a batch. */
internal sealed interface GameOutputSegment {
    data class Packets(val messages: List<OutgoingMessage>) : GameOutputSegment

    data class PacketGroup(val messages: List<OutgoingMessage>) : GameOutputSegment
}

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
