package emu.game.cycle

/** Aggregated timings for one completed reporting window. */
data class CycleProfileSnapshot(
    val cycles: Long,
    val averageNanos: Long,
    val maxNanos: Long,
    val lagSpikes: Long,
    val windowNanos: Long,
    val phases: List<CyclePhaseProfileSnapshot> = emptyList(),
)

/** Aggregated time spent in one global cycle phase during a reporting window. */
data class CyclePhaseProfileSnapshot(
    val phase: CyclePhase,
    val averageNanos: Long,
    val maxNanos: Long,
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
    private val phaseTotals = LongArray(CyclePhase.entries.size)
    private val phaseMaximums = LongArray(CyclePhase.entries.size)

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
                phases = phaseSnapshots(),
            )
        windowStartedAtNanos = finishedAtNanos
        cycles = 0
        totalNanos = 0
        maxNanos = 0
        lagSpikes = 0
        phaseTotals.fill(0)
        phaseMaximums.fill(0)
        return CycleProfileEvent(lagSpike, snapshot)
    }

    /** Adds one completed global phase to the current reporting window. */
    fun recordPhase(phase: CyclePhase, durationNanos: Long) {
        require(durationNanos >= 0) { "phase duration cannot be negative" }
        val index = phase.ordinal
        phaseTotals[index] += durationNanos
        phaseMaximums[index] = maxOf(phaseMaximums[index], durationNanos)
    }

    private fun phaseSnapshots(): List<CyclePhaseProfileSnapshot> =
        buildList {
            for (phase in CyclePhase.entries) {
                val index = phase.ordinal
                if (phaseTotals[index] == 0L) continue
                add(
                    CyclePhaseProfileSnapshot(
                        phase = phase,
                        averageNanos = phaseTotals[index] / cycles,
                        maxNanos = phaseMaximums[index],
                    ),
                )
            }
        }

    private companion object {
        const val DEFAULT_REPORT_INTERVAL_NANOS = 30_000_000_000L
        const val DEFAULT_TICK_BUDGET_NANOS = GAME_TICK_MILLIS * 1_000_000L
    }
}
