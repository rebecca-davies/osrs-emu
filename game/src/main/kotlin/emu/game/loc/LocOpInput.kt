package emu.game.loc

/** Revision-neutral payload for one client-selected loc menu operation. */
data class LocOpInput(
    val type: Int,
    val x: Int,
    val z: Int,
    val option: Int,
    val subOption: Int = 0,
    val controlKey: Boolean = false,
) {
    init {
        require(type in 0..0xFFFF) { "loc type must fit an unsigned short" }
        require(x in WORLD_COORDINATES && z in WORLD_COORDINATES) { "loc tile must be inside the game world" }
        require(option in 1..5) { "loc option must be in 1..5" }
        require(subOption in 0..0xFF) { "loc sub-option must fit an unsigned byte" }
    }

    private companion object {
        val WORLD_COORDINATES = 0..0x3FFF
    }
}
