package emu.server.world.runtime

import emu.game.cycle.CyclePhase

/** Validates unordered world-system contributions and compiles their deterministic cycle order. */
object WorldCyclePlanCompiler {
    fun compile(
        systems: Iterable<WorldSystem>,
        requiredPhases: Set<CyclePhase> = emptySet(),
    ): WorldCyclePlan {
        val contributions = systems.toList()
        rejectDuplicateIds(contributions)
        rejectDuplicateSlots(contributions)
        rejectMissingPhases(contributions, requiredPhases)
        val byPhase =
            contributions
                .groupBy(WorldSystem::phase)
                .mapValues { (_, phaseSystems) -> phaseSystems.sortedBy(WorldSystem::order) }
        return WorldCyclePlan(byPhase)
    }

    private fun rejectDuplicateIds(systems: List<WorldSystem>) {
        val duplicate = systems.groupingBy(WorldSystem::id).eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "duplicate world system id '${duplicate?.key}'" }
    }

    private fun rejectDuplicateSlots(systems: List<WorldSystem>) {
        val duplicate =
            systems
                .groupingBy { it.phase to it.order }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
        require(duplicate == null) {
            "duplicate world system slot ${duplicate?.first}:${duplicate?.second}"
        }
    }

    private fun rejectMissingPhases(
        systems: List<WorldSystem>,
        requiredPhases: Set<CyclePhase>,
    ) {
        val present = systems.mapTo(mutableSetOf(), WorldSystem::phase)
        val missing = requiredPhases.filterNot(present::contains).sortedBy(CyclePhase::ordinal)
        require(missing.isEmpty()) {
            "required world phases have no systems: ${missing.joinToString()}"
        }
    }
}
