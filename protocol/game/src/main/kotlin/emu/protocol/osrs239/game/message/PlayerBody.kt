package emu.protocol.osrs239.game.message

/** Equipment, recolours, and render animations in a revision-239 player appearance block. */
data class PlayerBody(
    val equipment: List<Int> = PlayerAppearance.DEFAULT_EQUIPMENT,
    val colors: List<Int> = PlayerAppearance.DEFAULT_COLORS,
    val animations: List<Int> = PlayerAppearance.DEFAULT_ANIMATIONS,
)
