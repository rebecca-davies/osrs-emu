package emu.game.action

import emu.game.chat.ChatInput
import emu.game.command.PlayerCommandInput
import emu.game.ui.ButtonClick

/** Immutable client action queued by network IO for ordered world-thread processing. */
sealed interface PlayerAction {
    data class Route(val x: Int, val y: Int, val invertRun: Boolean = false) : PlayerAction {
        init {
            require(x in WORLD_COORDINATES && y in WORLD_COORDINATES) {
                "route destination must be inside the game world"
            }
        }
    }

    data class Button(val click: ButtonClick) : PlayerAction

    data class Chat(val input: ChatInput) : PlayerAction

    data class Command(val input: PlayerCommandInput) : PlayerAction

    data object CloseModal : PlayerAction

    data object IdleLogout : PlayerAction

    private companion object {
        val WORLD_COORDINATES = 0..0x3FFF
    }
}
