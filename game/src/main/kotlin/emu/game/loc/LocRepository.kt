package emu.game.loc

import emu.game.map.Tile

/** Finds authoritative loc placements without performing cache IO on the world thread. */
fun interface LocRepository {
    fun find(type: Int, tile: Tile): Loc?

    companion object {
        val EMPTY = LocRepository { _, _ -> null }
    }
}
