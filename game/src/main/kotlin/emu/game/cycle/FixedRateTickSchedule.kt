package emu.game.cycle

/** The canonical RuneScape server cycle interval. */
const val GAME_TICK_MILLIS: Long = 600

/**
 * Absolute-deadline, fixed-rate game tick schedule.
 *
 * Work time is subtracted from the next delay. An overrun produces a zero delay, but the deadline
 * stays on the original timeline so latency does not accumulate from one cycle to the next.
 */
class FixedRateTickSchedule(private val intervalMillis: Long = GAME_TICK_MILLIS) {
    private var nextDeadlineMillis: Long? = null

    init {
        require(intervalMillis > 0) { "tick interval must be positive" }
    }

    /** Returns the non-negative wait required after a tick spanning the supplied timestamps. */
    fun delayAfterTick(startedAtMillis: Long, finishedAtMillis: Long): Long {
        require(finishedAtMillis >= startedAtMillis) { "tick finish precedes its start" }
        val deadline = Math.addExact(nextDeadlineMillis ?: startedAtMillis, intervalMillis)
        nextDeadlineMillis = deadline
        return maxOf(0L, deadline - finishedAtMillis)
    }
}
