package emu.game.input

import java.util.concurrent.ArrayBlockingQueue

/**
 * Bounded FIFO transfer from one connection's network coroutine to the world thread.
 *
 * [drain] processes at most the configured cycle budget and leaves later inputs ordered for the
 * next cycle. A full queue rejects the newest input without modifying admitted input.
 */
class PlayerInputQueue(
    config: PlayerInputQueueConfig,
) : PlayerInputSink {
    private val inputs: ArrayBlockingQueue<PlayerInput>
    private val maxPerCycle = config.maxPerCycle

    init {
        inputs = ArrayBlockingQueue(config.capacity)
    }

    override fun submit(input: PlayerInput): Boolean = inputs.offer(input)

    /** Processes the next ordered cycle budget on the calling world thread. */
    fun drain(consumer: (PlayerInput) -> Unit): Int {
        var processed = 0
        repeat(maxPerCycle) {
            val input = inputs.poll() ?: return processed
            consumer(input)
            processed++
        }
        return processed
    }
}
