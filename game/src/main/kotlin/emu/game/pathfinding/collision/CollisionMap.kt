package emu.game.pathfinding.collision

/** Read-only collision source consumed by path searches and per-cycle step validation. */
fun interface CollisionMap {
    /** Returns the flags at a tile; unknown/unallocated tiles should return `-1` so they block. */
    fun flagsAt(x: Int, y: Int, plane: Int): Int
}

/** Collision source where every valid world tile is traversable. */
data object OpenCollisionMap : CollisionMap {
    override fun flagsAt(x: Int, y: Int, plane: Int): Int =
        if (x in 0..0x3FFF && y in 0..0x3FFF && plane in 0..3) 0 else -1
}

/** Tests one allocation-free size-one step with the same destination and corner masks as Blurite. */
internal fun CollisionMap.canTravel(
    x: Int,
    y: Int,
    plane: Int,
    deltaX: Int,
    deltaY: Int,
    extraFlag: Int = 0,
): Boolean {
    require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
        "step delta must be one adjacent tile"
    }
    return when ((deltaY + 1) * 3 + deltaX + 1) {
        3 -> clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag)
        5 -> clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag)
        1 -> clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        7 -> clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        0 ->
            clear(x - 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_WEST, extraFlag) &&
                clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag) &&
                clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        2 ->
            clear(x + 1, y - 1, plane, CollisionFlag.BLOCK_SOUTH_EAST, extraFlag) &&
                clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag) &&
                clear(x, y - 1, plane, CollisionFlag.BLOCK_SOUTH, extraFlag)
        6 ->
            clear(x - 1, y + 1, plane, CollisionFlag.BLOCK_NORTH_WEST, extraFlag) &&
                clear(x - 1, y, plane, CollisionFlag.BLOCK_WEST, extraFlag) &&
                clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        8 ->
            clear(x + 1, y + 1, plane, CollisionFlag.BLOCK_NORTH_EAST, extraFlag) &&
                clear(x + 1, y, plane, CollisionFlag.BLOCK_EAST, extraFlag) &&
                clear(x, y + 1, plane, CollisionFlag.BLOCK_NORTH, extraFlag)
        else -> false
    }
}

private fun CollisionMap.clear(
    x: Int,
    y: Int,
    plane: Int,
    mask: Int,
    extraFlag: Int,
): Boolean = flagsAt(x, y, plane) and (mask or extraFlag) == 0
