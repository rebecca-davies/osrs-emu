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

    /** Lowest tick accepted by the next [tick] call. */
    var currentTick: Long = 0
        private set

    /** Executes one complete cycle using the next local tick. Primarily useful in focused tests. */
    suspend fun tick(): Long = tick(currentTick)

    /**
     * Executes one complete cycle using the server-owned [worldTick]. A participant may join after
     * tick zero, but an already-processed tick can never be replayed. The clock advances only after
     * every phase succeeds.
     */
    suspend fun tick(worldTick: Long): Long {
        require(worldTick >= currentTick) {
            "world tick $worldTick precedes next accepted tick $currentTick"
        }
        for (phase in CyclePhase.entries) {
            for (process in processesByPhase[phase].orEmpty()) {
                process.process(worldTick)
            }
        }
        currentTick = Math.addExact(worldTick, 1L)
        return worldTick
    }
}
