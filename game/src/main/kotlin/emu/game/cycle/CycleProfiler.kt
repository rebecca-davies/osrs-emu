package emu.game.cycle

/** Aggregated timings for one completed reporting window. */
data class CycleProfileSnapshot(
    val cycles: Long,
    val averageNanos: Long,
    val maxNanos: Long,
    val lagSpikes: Long,
    val windowNanos: Long,
)

/** Result of recording one cycle; a snapshot is present only when a reporting window closes. */
data class CycleProfileEvent(
    val lagSpike: Boolean,
    val snapshot: CycleProfileSnapshot?,
)

/**
 * Allocation-light rolling game-cycle profiler. Callers provide monotonic nanosecond timestamps,
 * which keeps wall-clock adjustments from manufacturing false spikes and makes the aggregation
 * deterministic in tests.
 */
class CycleProfiler(
    private val reportIntervalNanos: Long = DEFAULT_REPORT_INTERVAL_NANOS,
    private val tickBudgetNanos: Long = DEFAULT_TICK_BUDGET_NANOS,
    startedAtNanos: Long = System.nanoTime(),
) {
    private var windowStartedAtNanos = startedAtNanos
    private var cycles = 0L
    private var totalNanos = 0L
    private var maxNanos = 0L
    private var lagSpikes = 0L

    init {
        require(reportIntervalNanos > 0) { "report interval must be positive" }
        require(tickBudgetNanos > 0) { "tick budget must be positive" }
    }

    fun record(durationNanos: Long, finishedAtNanos: Long = System.nanoTime()): CycleProfileEvent {
        require(durationNanos >= 0) { "cycle duration cannot be negative" }
        require(finishedAtNanos >= windowStartedAtNanos) { "monotonic clock moved backwards" }
        val lagSpike = durationNanos > tickBudgetNanos
        cycles++
        totalNanos += durationNanos
        maxNanos = maxOf(maxNanos, durationNanos)
        if (lagSpike) lagSpikes++

        val elapsed = finishedAtNanos - windowStartedAtNanos
        if (elapsed < reportIntervalNanos) return CycleProfileEvent(lagSpike, null)

        val snapshot =
            CycleProfileSnapshot(
                cycles = cycles,
                averageNanos = totalNanos / cycles,
                maxNanos = maxNanos,
                lagSpikes = lagSpikes,
                windowNanos = elapsed,
            )
        windowStartedAtNanos = finishedAtNanos
        cycles = 0
        totalNanos = 0
        maxNanos = 0
        lagSpikes = 0
        return CycleProfileEvent(lagSpike, snapshot)
    }

    private companion object {
        const val DEFAULT_REPORT_INTERVAL_NANOS = 30_000_000_000L
        const val DEFAULT_TICK_BUDGET_NANOS = GAME_TICK_MILLIS * 1_000_000L
    }
}
