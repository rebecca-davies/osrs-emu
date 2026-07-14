package emu.game.map

import emu.game.pathfinding.Tile

/**
 * The local player's normal 104x104 scene window.
 *
 * Its south-west base is six 8x8 zones below the centre zone. It recentres when the player enters
 * the outer two-zone boundary, matching OSRS's normal build-area lifecycle.
 */
class PlayerBuildArea(initialPosition: Tile) {
    var baseX: Int = 0
        private set

    var baseY: Int = 0
        private set

    val centreZoneX: Int
        get() = (baseX shr ZONE_SHIFT) + ZONE_RADIUS

    val centreZoneY: Int
        get() = (baseY shr ZONE_SHIFT) + ZONE_RADIUS

    init {
        recenter(initialPosition)
    }

    fun localX(worldX: Int): Int = worldX - baseX

    fun localY(worldY: Int): Int = worldY - baseY

    /** Recentres around [position] when it has entered the outer 16 tiles. */
    fun recenterIfRequired(position: Tile): Boolean {
        val localX = localX(position.x)
        val localY = localY(position.y)
        if (localX in SAFE_COORDINATES && localY in SAFE_COORDINATES) return false
        recenter(position)
        return true
    }

    private fun recenter(position: Tile) {
        baseX = ((position.x shr ZONE_SHIFT) - ZONE_RADIUS) shl ZONE_SHIFT
        baseY = ((position.y shr ZONE_SHIFT) - ZONE_RADIUS) shl ZONE_SHIFT
    }

    private companion object {
        const val ZONE_SHIFT = 3
        const val ZONE_RADIUS = 6
        const val REBUILD_BOUNDARY = 16
        const val SCENE_SIZE = 104
        val SAFE_COORDINATES = REBUILD_BOUNDARY until SCENE_SIZE - REBUILD_BOUNDARY
    }
}
