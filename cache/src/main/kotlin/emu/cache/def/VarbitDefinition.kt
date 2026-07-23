package emu.cache.def

/** Decoded rev-239 varbit definition from index 2, group 14. */
data class VarbitDefinition(
    val id: Int,
    val baseVar: Int,
    val bits: IntRange,
) {
    init {
        require(id >= 0) { "varbit id must be non-negative" }
        require(baseVar in 0..0xFFFF) { "varbit base varp must fit an unsigned short" }
        require(bits.first in 0..31 && bits.last in bits.first..31) { "invalid varbit range $bits" }
    }
}
