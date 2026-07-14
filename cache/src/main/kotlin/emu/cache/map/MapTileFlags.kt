package emu.cache.map

/** Movement-relevant terrain rules for one 64x64, four-plane map square. */
class MapTileFlags internal constructor(private val packed: ByteArray = ByteArray(TILE_COUNT)) {
    /** Returns the decoded terrain flags for one map-square-local tile. */
    operator fun get(localX: Int, localY: Int, plane: Int): Int =
        packed[index(localX, localY, plane)].toInt() and 0xFF

    internal fun add(localX: Int, localY: Int, plane: Int, flag: Int) {
        val index = index(localX, localY, plane)
        packed[index] = (packed[index].toInt() or flag).toByte()
    }

    private fun index(localX: Int, localY: Int, plane: Int): Int {
        require(localX in 0 until MAP_SQUARE_SIZE) { "local x outside map square: $localX" }
        require(localY in 0 until MAP_SQUARE_SIZE) { "local y outside map square: $localY" }
        require(plane in 0 until PLANE_COUNT) { "plane outside map square: $plane" }
        return localY or (localX shl 6) or (plane shl 12)
    }

    companion object {
        const val MAP_SQUARE_SIZE: Int = 64
        const val PLANE_COUNT: Int = 4
        const val BLOCK_MOVEMENT: Int = 0x1
        const val LINK_BELOW: Int = 0x2
        const val REMOVE_ROOFS: Int = 0x4

        private const val TILE_COUNT: Int = PLANE_COUNT * MAP_SQUARE_SIZE * MAP_SQUARE_SIZE
    }
}
