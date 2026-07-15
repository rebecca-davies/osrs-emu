package emu.server.world.runtime

import emu.game.cycle.CyclePhase

/** Immutable, validated execution order for one authoritative world cycle. */
class WorldCyclePlan internal constructor(
    private val systemsByPhase: Map<CyclePhase, List<WorldSystem>>,
) {
    fun execute(tick: WorldTick) {
        for (phase in CyclePhase.entries) {
            for (system in systemsByPhase[phase].orEmpty()) {
                system.execute(tick)
            }
        }
    }
}
