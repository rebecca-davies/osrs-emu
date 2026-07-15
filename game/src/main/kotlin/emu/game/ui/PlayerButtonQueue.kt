package emu.game.ui

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import java.util.concurrent.ArrayBlockingQueue

/** Thread-safe interface-click sink exposed to packet handlers. */
fun interface PlayerButtonSink {
    fun submit(click: ButtonClick): Boolean
}

/**
 * Bounded network-to-world mailbox for interface clicks.
 *
 * Network coroutines only [submit]. Registered content actions run during `CLIENT_INPUT` on the
 * authoritative game-cycle thread, so a slow or malicious client cannot mutate player state from
 * gateway I/O or create an unbounded queue.
 */
class PlayerButtonQueue(
    capacity: Int = DEFAULT_CAPACITY,
    private val maxPerCycle: Int = DEFAULT_MAX_PER_CYCLE,
) : PlayerButtonSink {
    private val clicks: ArrayBlockingQueue<ButtonClick>

    init {
        require(capacity > 0) { "button queue capacity must be positive" }
        require(maxPerCycle > 0) { "per-cycle button limit must be positive" }
        clicks = ArrayBlockingQueue(capacity)
    }

    override fun submit(click: ButtonClick): Boolean = clicks.offer(click)

    fun cycleProcesses(actions: ButtonActionRegistry): List<CycleProcess> =
        listOf(
            CycleProcess(CyclePhase.CLIENT_INPUT) {
                repeat(maxPerCycle) {
                    val click = clicks.poll() ?: return@CycleProcess
                    actions.dispatch(click)
                }
            },
        )

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val DEFAULT_MAX_PER_CYCLE = 20
    }
}
