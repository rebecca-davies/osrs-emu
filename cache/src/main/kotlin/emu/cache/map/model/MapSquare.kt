package emu.cache.map.model

/** Decoded terrain and static loc placements for one 64x64 cache map square. */
data class MapSquare(
    val squareX: Int,
    val squareY: Int,
    val tiles: MapTileFlags,
    val locs: List<MapLocSpawn>,
) {
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
}
