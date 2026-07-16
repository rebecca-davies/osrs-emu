package emu.game.varp

/** Whether a player varp survives logout. */
enum class VarpScope {
    TEMPORARY,
    PERMANENT,
}

/** Client synchronization policy for a player varp. */
enum class VarpTransmit {
    NEVER,
    ON_CHANGE,
    ALWAYS,
}

/** Typed metadata for one 32-bit player variable. */
data class VarpType(
    val id: Int,
    val scope: VarpScope = VarpScope.TEMPORARY,
    val transmit: VarpTransmit = VarpTransmit.ON_CHANGE,
) {
    init {
        require(id in 0..0xFFFF) { "varp id must fit an unsigned short" }
    }
}

/** A bitfield backed by one [VarpType]. */
data class VarbitType(
    val id: Int,
    val baseVar: VarpType,
    val bits: IntRange,
) {
    init {
        require(id >= 0) { "varbit id must be non-negative" }
        require(bits.first in 0..31 && bits.last in bits.first..31) { "invalid varbit range $bits" }
    }

    val maxValue: Long
        get() = if (bits.count() == 32) 0xFFFF_FFFFL else (1L shl bits.count()) - 1L
}

/** One player-variable value ready for synchronization or persistence. */
data class VarpValue(val id: Int, val value: Int)

/** Immutable set of varps understood by this game revision and content build. */
class VarpCatalog(vararg types: VarpType) {
    private val byId: Map<Int, VarpType>

    init {
        val duplicate = types.groupingBy(VarpType::id).eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "duplicate varp id ${duplicate?.key}" }
        byId = types.associateBy(VarpType::id)
    }

    val types: List<VarpType>
        get() = byId.values.sortedBy(VarpType::id)

    operator fun get(id: Int): VarpType? = byId[id]
}
