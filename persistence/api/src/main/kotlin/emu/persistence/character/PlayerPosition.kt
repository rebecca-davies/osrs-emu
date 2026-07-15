package emu.persistence.character

private val WORLD_COORDINATES = 0..0x3FFF
private val WORLD_PLANES = 0..3

/** Persisted world position independent of live movement state. */
data class PlayerPosition(val x: Int, val y: Int, val plane: Int) {
    init {
        require(x in WORLD_COORDINATES && y in WORLD_COORDINATES) { "position outside world: $this" }
        require(plane in WORLD_PLANES) { "invalid plane $plane" }
    }
}
