package emu.cache.map.model

/** Decoded terrain and static loc placements for one 64x64 cache map square. */
data class MapSquare(
    val squareX: Int,
    val squareY: Int,
    val tiles: MapTileFlags,
    val locs: List<MapLocSpawn>,
)
