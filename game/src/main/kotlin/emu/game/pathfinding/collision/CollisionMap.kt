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
