package emu.game.content.ui

import emu.game.ui.Component

/** One interface attached beneath a destination component in the gameframe tree. */
data class GameframeSubInterface(
    val destination: Component,
    val interfaceId: Int,
    val modal: Boolean = false,
) {
    init {
        require(interfaceId in 0..0xFFFF) { "subinterface id must fit an unsigned short" }
    }
}
