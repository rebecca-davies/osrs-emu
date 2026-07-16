package emu.server.game.world.map

import emu.game.map.Tile

/** Prepares cache collision before entry or requests bounded preparation from a world cycle. */
interface CollisionMapLoader {
    /** Blocks the caller until the pathfinding window around [position] is prepared. */
    fun prepare(position: Tile)

    /** Submits preparation without blocking, returning false only when it cannot be accepted. */
    fun request(position: Tile): Boolean
}
