package emu.game.map

/** Absolute RuneScape tile coordinate. */
data class Tile(val x: Int, val y: Int, val plane: Int = 0) {
    init {
        require(x in 0..0x3FFF) { "x must fit the 14-bit world coordinate" }
        require(y in 0..0x3FFF) { "y must fit the 14-bit world coordinate" }
        require(plane in 0..3) { "plane must be in 0..3" }
    }
}
