package emu.game.loc

import emu.game.map.Tile

/** One authoritative loc placement and its cache-defined interaction footprint. */
data class Loc(
    val type: Int,
    val tile: Tile,
    val shape: Int,
    val angle: Int,
    val width: Int,
    val length: Int,
    val options: Set<Int>,
    val subOptions: Map<Int, Set<Int>> = emptyMap(),
) {
    init {
        require(type in 0..0xFFFF) { "loc type must fit an unsigned short" }
        require(shape in 0..31) { "loc shape must fit five bits" }
        require(angle in 0..3) { "loc angle must be in 0..3" }
        require(width > 0 && length > 0) { "loc footprint must be positive" }
        require(options.all { it in 1..5 }) { "loc options must be in 1..5" }
        require(subOptions.keys.all { it in options }) { "loc sub-options require a matching option" }
    }

    /** Whether the cache exposes this exact menu operation and nested sub-option. */
    fun supports(option: Int, subOption: Int): Boolean =
        if (subOption == 0) option in options else subOption in subOptions[option].orEmpty()

    /** Whether [position] is outside and directly adjacent to this loc's occupied rectangle. */
    fun isAdjacentTo(position: Tile): Boolean {
        if (position.plane != tile.plane) return false
        val east = tile.x + width - 1
        val north = tile.y + length - 1
        return ((position.x == tile.x - 1 || position.x == east + 1) && position.y in tile.y..north) ||
            ((position.y == tile.y - 1 || position.y == north + 1) && position.x in tile.x..east)
    }
}
