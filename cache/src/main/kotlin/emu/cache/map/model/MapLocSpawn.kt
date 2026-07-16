package emu.cache.map.model

/** One static loc placement decoded from a map square's `lX_Y` group. */
data class MapLocSpawn(
    val id: Int,
    val localX: Int,
    val localY: Int,
    val plane: Int,
    val shape: Int,
    val rotation: Int,
)
