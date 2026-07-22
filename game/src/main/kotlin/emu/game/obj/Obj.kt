package emu.game.obj

/** One object stack held by a player inventory. */
data class Obj(val type: Int, val count: Int = 1) {
    init {
        require(type in 0..0xFFFF) { "object type must fit an unsigned short" }
        require(count > 0) { "object count must be positive" }
    }
}
