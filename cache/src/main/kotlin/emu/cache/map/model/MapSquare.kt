package emu.cache.map.model

import java.util.Arrays

/** Decoded terrain and static loc placements for one 64x64 cache map square. */
data class MapSquare(
    val squareX: Int,
    val squareY: Int,
    val tiles: MapTileFlags,
    val locs: List<MapLocSpawn>,
) {
    init {
        for (loc in locs) {
            require(loc.id in LOC_TYPE_RANGE) { "loc type must fit an unsigned short" }
            require(loc.plane in PLANE_RANGE) { "loc plane must be in 0..3" }
            require(loc.localX in LOCAL_COORDINATE_RANGE && loc.localY in LOCAL_COORDINATE_RANGE) {
                "loc coordinates must be inside their map square"
            }
        }
    }

    private val locsByOrdinal = locs.toTypedArray()
    private val locsByPosition = LongArray(locsByOrdinal.size)
    private val indexedLocCount =
        run {
            var count = 0
            for (ordinal in locsByOrdinal.indices) {
                val loc = locsByOrdinal[ordinal]
                val plane = visualPlane(loc)
                if (plane < 0) continue
                val key = placementKey(loc.id, plane, loc.localX, loc.localY)
                locsByPosition[count++] = (key.toLong() shl KEY_SHIFT) or ordinal.toLong()
            }
            Arrays.sort(locsByPosition, 0, count)
            count
        }

    /** Resolves the level where the client displays [loc], including link-below terrain. */
    fun visualPlane(loc: MapLocSpawn): Int {
        require(loc.localX in 0 until MapTileFlags.MAP_SQUARE_SIZE)
        require(loc.localY in 0 until MapTileFlags.MAP_SQUARE_SIZE)
        require(loc.plane in 0 until MapTileFlags.PLANE_COUNT)
        val tileFlags = tiles[loc.localX, loc.localY, loc.plane]
        val tileAboveFlags =
            if (loc.plane == MapTileFlags.PLANE_COUNT - 1) {
                tileFlags
            } else {
                tiles[loc.localX, loc.localY, loc.plane + 1]
            }
        val resolvedFlags =
            if (tileAboveFlags and MapTileFlags.LINK_BELOW != 0) tileAboveFlags else tileFlags
        return if (resolvedFlags and MapTileFlags.LINK_BELOW != 0) loc.plane - 1 else loc.plane
    }

    /** Returns the first matching static loc, or null when any key component is out of range. */
    fun findLoc(type: Int, plane: Int, localX: Int, localY: Int): MapLocSpawn? {
        if (
            type !in LOC_TYPE_RANGE || plane !in PLANE_RANGE ||
            localX !in LOCAL_COORDINATE_RANGE || localY !in LOCAL_COORDINATE_RANGE
        ) {
            return null
        }
        val key = placementKey(type, plane, localX, localY)
        var low = 0
        var high = indexedLocCount
        while (low < high) {
            val middle = (low + high) ushr 1
            if (locsByPosition[middle] ushr KEY_SHIFT < key.toLong()) {
                low = middle + 1
            } else {
                high = middle
            }
        }
        if (low >= indexedLocCount || locsByPosition[low] ushr KEY_SHIFT != key.toLong()) return null
        return locsByOrdinal[locsByPosition[low].toInt()]
    }

    private companion object {
        val LOC_TYPE_RANGE = 0..0xFFFF
        val PLANE_RANGE = 0 until MapTileFlags.PLANE_COUNT
        val LOCAL_COORDINATE_RANGE = 0 until MapTileFlags.MAP_SQUARE_SIZE
        const val KEY_SHIFT = Int.SIZE_BITS

        fun placementKey(type: Int, plane: Int, localX: Int, localY: Int): Int =
            (type shl 14) or (plane shl 12) or (localX shl 6) or localY
    }
}
