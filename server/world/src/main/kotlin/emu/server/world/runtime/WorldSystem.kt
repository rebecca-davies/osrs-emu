package emu.server.world.runtime

import emu.game.cycle.CyclePhase

/** One non-suspending world-scoped operation assigned to a deterministic cycle slot. */
interface WorldSystem {
    val id: WorldSystemId
    val phase: CyclePhase
    val order: Int

    fun execute(tick: WorldTick)
}
