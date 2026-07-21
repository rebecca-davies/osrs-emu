package emu.game.player

/** One player animation request for the current cycle. */
data class PlayerAnimation(
    val id: Int,
    val delay: Int = 0,
) {
    init {
        require(id in -1..MAX_ANIMATION_ID) { "animation id must be in -1..$MAX_ANIMATION_ID" }
        require(delay in UBYTE_RANGE) { "animation delay must fit an unsigned byte" }
    }

    private companion object {
        const val MAX_ANIMATION_ID = 0xFFFE
        val UBYTE_RANGE = 0..0xFF
    }
}
