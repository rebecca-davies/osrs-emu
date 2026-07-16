package emu.server.game.network.output

/** Non-suspending boundary from the authoritative world cycle to connection-owned output. */
internal fun interface GameOutputSink {
    fun offer(batch: GameOutputBatch): Boolean
}
