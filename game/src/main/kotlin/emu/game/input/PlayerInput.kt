package emu.game.input

import emu.game.chat.ChatInput
import emu.game.ui.ButtonClick

/** Immutable player input admitted from network IO for ordered world-thread processing. */
sealed interface PlayerInput {
    data class Route(val x: Int, val y: Int, val invertRun: Boolean) : PlayerInput {
        init {
            require(x in WORLD_COORDINATES && y in WORLD_COORDINATES) {
                "route destination must be inside the game world"
            }
        }
    }

    data class Button(val click: ButtonClick) : PlayerInput

    data class Chat(val input: ChatInput) : PlayerInput

    private companion object {
        val WORLD_COORDINATES = 0..0x3FFF
    }
}
