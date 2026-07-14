package emu.game.pathfinding

/** Read-only collision source consumed by path searches and per-cycle step validation. */
fun interface CollisionMap {
    /** Returns the flags at a tile; unknown/unallocated tiles should return `-1` so they block. */
    fun flagsAt(x: Int, y: Int, plane: Int): Int
}

/** Collision source for scaffolding and tests where every valid world tile is traversable. */
data object OpenCollisionMap : CollisionMap {
    override fun flagsAt(x: Int, y: Int, plane: Int): Int =
        if (x in 0..0x3FFF && y in 0..0x3FFF && plane in 0..3) 0 else -1
}

/** Sparse mutable collision map suitable for map-building and dynamic entity blockers. */
class MutableCollisionMap(private val defaultFlag: Int = 0) : CollisionMap {
    private val flags = mutableMapOf<Long, Int>()

    override fun flagsAt(x: Int, y: Int, plane: Int): Int {
        if (x !in 0..0x3FFF || y !in 0..0x3FFF || plane !in 0..3) return -1
        return flags[pack(x, y, plane)] ?: defaultFlag
    }

    operator fun set(x: Int, y: Int, plane: Int, flag: Int) {
        flags[pack(x, y, plane)] = flag
    }

    fun add(x: Int, y: Int, plane: Int, flag: Int) {
        this[x, y, plane] = flagsAt(x, y, plane) or flag
    }

    fun remove(x: Int, y: Int, plane: Int, flag: Int) {
        this[x, y, plane] = flagsAt(x, y, plane) and flag.inv()
    }

    private fun pack(x: Int, y: Int, plane: Int): Long =
        (plane.toLong() shl 28) or (x.toLong() shl 14) or y.toLong()
}

/** Tests a single size-one step with the same destination and corner masks as Blurite. */
internal fun CollisionMap.canTravel(
    position: Tile,
    deltaX: Int,
    deltaY: Int,
    extraFlag: Int = 0,
): Boolean {
    require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
        "step delta must be one adjacent tile"
    }
    val x = position.x
    val y = position.y
    val z = position.plane
    fun clear(atX: Int, atY: Int, mask: Int): Boolean =
        flagsAt(atX, atY, z) and (mask or extraFlag) == 0

    return when (deltaX to deltaY) {
        -1 to 0 -> clear(x - 1, y, CollisionFlag.BLOCK_WEST)
        1 to 0 -> clear(x + 1, y, CollisionFlag.BLOCK_EAST)
        0 to -1 -> clear(x, y - 1, CollisionFlag.BLOCK_SOUTH)
        0 to 1 -> clear(x, y + 1, CollisionFlag.BLOCK_NORTH)
        -1 to -1 ->
            clear(x - 1, y - 1, CollisionFlag.BLOCK_SOUTH_WEST) &&
                clear(x - 1, y, CollisionFlag.BLOCK_WEST) &&
                clear(x, y - 1, CollisionFlag.BLOCK_SOUTH)
        1 to -1 ->
            clear(x + 1, y - 1, CollisionFlag.BLOCK_SOUTH_EAST) &&
                clear(x + 1, y, CollisionFlag.BLOCK_EAST) &&
                clear(x, y - 1, CollisionFlag.BLOCK_SOUTH)
        -1 to 1 ->
            clear(x - 1, y + 1, CollisionFlag.BLOCK_NORTH_WEST) &&
                clear(x - 1, y, CollisionFlag.BLOCK_WEST) &&
                clear(x, y + 1, CollisionFlag.BLOCK_NORTH)
        1 to 1 ->
            clear(x + 1, y + 1, CollisionFlag.BLOCK_NORTH_EAST) &&
                clear(x + 1, y, CollisionFlag.BLOCK_EAST) &&
                clear(x, y + 1, CollisionFlag.BLOCK_NORTH)
        else -> false
    }
}
