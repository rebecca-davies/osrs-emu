package emu.game.content.ui

/** Empty client inventory published while the initial gameframe is opened. */
data class GameframeInventory(
    val componentId: Int,
    val inventoryId: Int,
) {
    init {
        require(componentId in 0..0xFFFF) { "component id must fit an unsigned short" }
        require(inventoryId in 0..0xFFFF) { "inventory id must fit an unsigned short" }
    }
}
