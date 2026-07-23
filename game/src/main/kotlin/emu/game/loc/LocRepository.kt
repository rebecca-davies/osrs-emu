package emu.game.loc

import emu.game.map.Tile

/** Finds authoritative loc placements without performing cache IO on the world thread. */
fun interface LocRepository {
    fun find(type: Int, tile: Tile): Loc?

    /** Whether [loc] is still the authoritative placement at its recorded tile. */
    fun isCurrent(loc: Loc): Boolean = find(loc.type, loc.tile) == loc

    companion object {
        val EMPTY = LocRepository { _, _ -> null }
    }
}
