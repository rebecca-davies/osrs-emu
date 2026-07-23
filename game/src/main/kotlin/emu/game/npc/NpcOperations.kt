package emu.game.npc

import emu.game.varp.PlayerVariable
import emu.game.varp.PlayerVariableValues

/** Immutable cache-defined NPC menu operations, including player-variable alternatives. */
data class NpcOperations(
    val options: Set<Int> = emptySet(),
    val subOptions: Map<Int, Set<Int>> = emptyMap(),
    val conditionalOptions: Map<Int, List<NpcConditionalOperation>> = emptyMap(),
    val conditionalSubOptions: Map<Int, Map<Int, List<NpcConditionalOperation>>> = emptyMap(),
) {
    init {
        require(options.all(::validOption)) { "NPC options must be in 1..5" }
        require(subOptions.keys.all(::validOption)) { "NPC sub-option parents must be in 1..5" }
        require(subOptions.values.flatten().all(::validSubOption)) {
            "NPC sub-options must fit a non-zero unsigned byte"
        }
        require(conditionalOptions.keys.all(::validOption)) { "conditional NPC options must be in 1..5" }
        require(conditionalSubOptions.keys.all(::validOption)) {
            "conditional NPC sub-option parents must be in 1..5"
        }
        require(conditionalSubOptions.values.flatMap(Map<Int, *>::keys).all(::validSubOption)) {
            "conditional NPC sub-options must fit a non-zero unsigned byte"
        }
    }

    /** Whether this player's current variables expose the exact menu operation. */
    fun supports(option: Int, subOption: Int, variables: PlayerVariableValues): Boolean {
        if (!validOption(option)) return false
        return if (subOption == 0) {
            conditionalOptions[option].resolvedVisibility(variables) ?: (option in options)
        } else {
            if (!validSubOption(subOption)) return false
            conditionalSubOptions[option]?.get(subOption).resolvedVisibility(variables)
                ?: (subOption in subOptions[option].orEmpty())
        }
    }

    private fun List<NpcConditionalOperation>?.resolvedVisibility(
        variables: PlayerVariableValues,
    ): Boolean? = this?.firstOrNull { it.matches(variables) }?.visible

    companion object {
        val EMPTY = NpcOperations()

        private fun validOption(value: Int): Boolean = value in 1..5

        private fun validSubOption(value: Int): Boolean = value in 1..0xFF
    }
}

/** One cache operation selected when [variable] falls inside its inclusive value range. */
data class NpcConditionalOperation(
    val variable: PlayerVariable,
    val values: IntRange,
    val visible: Boolean,
) {
    fun matches(variables: PlayerVariableValues): Boolean = variables[variable] in values
}

/** Player-variable transform destinations, with the final entry used as the fallback. */
data class NpcTransform(
    val variable: PlayerVariable,
    val destinations: List<Int>,
) {
    init {
        require(destinations.isNotEmpty()) { "NPC transform must have a fallback destination" }
        require(destinations.all { it in -1..NpcType.MAX_ID }) { "NPC transform destination is out of range" }
    }

    fun destination(variables: PlayerVariableValues): Int {
        val selector = variables[variable]
        return if (selector in 0 until destinations.lastIndex) destinations[selector] else destinations.last()
    }
}
