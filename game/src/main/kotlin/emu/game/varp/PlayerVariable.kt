package emu.game.varp

/** Cache-addressable player variable used by client-side transforms and conditional operations. */
sealed interface PlayerVariable {
    val id: Int

    data class Varp(override val id: Int) : PlayerVariable {
        init {
            require(id in 0..0xFFFF) { "varp id must fit an unsigned short" }
        }
    }

    data class Varbit(
        override val id: Int,
        val baseVar: Int,
        val bits: IntRange,
    ) : PlayerVariable {
        init {
            require(id >= 0) { "varbit id must be non-negative" }
            require(baseVar in 0..0xFFFF) { "varbit base varp must fit an unsigned short" }
            require(bits.first in 0..31 && bits.last in bits.first..31) { "invalid varbit range $bits" }
        }
    }
}

/** Read-only player-variable values used by cache-defined client behavior. */
fun interface PlayerVariableValues {
    operator fun get(variable: PlayerVariable): Int
}
