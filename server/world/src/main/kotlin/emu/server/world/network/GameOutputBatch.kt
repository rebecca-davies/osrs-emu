package emu.server.world.network

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
