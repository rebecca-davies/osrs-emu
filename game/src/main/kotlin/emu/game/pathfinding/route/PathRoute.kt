package emu.game.pathfinding.route

import emu.game.map.Tile

/** Compressed turnpoint route produced by a path search. */
data class PathRoute(
    val waypoints: List<Tile>,
    val alternative: Boolean,
    val success: Boolean,
) {
    val failed: Boolean
        get() = !success

    companion object {
        val Failed = PathRoute(emptyList(), alternative = false, success = false)
    }
}
