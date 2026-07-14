package emu.game.cycle

/** A small unit of work assigned to one fixed [phase] of the world cycle. */
class CycleProcess(
    val phase: CyclePhase,
    val process: suspend (tick: Long) -> Unit,
)

/**
 * Deterministic, single-thread-owned world-cycle coordinator.
 *
 * Registration order is retained within a phase, while phases themselves always execute in
 * [CyclePhase] declaration order. The clock advances only after every phase succeeds, making a
 * failed cycle visible rather than silently skipping a simulation tick.
 */
class GameCycle(processes: Iterable<CycleProcess> = emptyList()) {
    private val processesByPhase: Map<CyclePhase, List<CycleProcess>> =
        processes.groupBy(CycleProcess::phase)

    /** Tick being processed by the next [tick] call. */
    var currentTick: Long = 0
        private set

    /** Executes one complete cycle and returns the tick number that was processed. */
    suspend fun tick(): Long {
        val processedTick = currentTick
        for (phase in CyclePhase.entries) {
            for (process in processesByPhase[phase].orEmpty()) {
                process.process(processedTick)
            }
        }
        currentTick++
        return processedTick
    }
}
