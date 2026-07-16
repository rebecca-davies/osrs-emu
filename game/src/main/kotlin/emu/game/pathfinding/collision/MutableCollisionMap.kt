package emu.game.pathfinding.collision

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
