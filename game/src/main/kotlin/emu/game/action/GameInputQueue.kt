package emu.game.action

import java.util.concurrent.ArrayBlockingQueue

/**
 * Bounded FIFO transfer from one connection's network coroutine to the world thread.
 *
 * [drain] processes at most the configured cycle budget and leaves later actions ordered for the
 * next cycle. A full queue rejects the newest action without modifying accepted work.
 */
class GameInputQueue(
    config: GameInputQueueConfig,
) : PlayerActionSink {
    private val actions = ArrayBlockingQueue<PlayerAction>(config.capacity)
    private val maxPerCycle = config.maxPerCycle

    override fun submit(action: PlayerAction): Boolean = actions.offer(action)

    /** Processes the next ordered cycle budget on the calling world thread. */
    fun drain(consumer: (PlayerAction) -> Unit): Int {
        var processed = 0
        repeat(maxPerCycle) {
            val action = actions.poll() ?: return processed
            consumer(action)
            processed++
        }
        return processed
    }
}
