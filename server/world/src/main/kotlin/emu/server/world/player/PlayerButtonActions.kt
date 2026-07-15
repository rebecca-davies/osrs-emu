package emu.server.world.player

import emu.game.pathfinding.PlayerMovement
import emu.game.ui.ButtonAction
import emu.game.ui.ButtonActionRegistry
import emu.game.ui.buttonActions
import emu.game.varp.PlayerVarps

/** Registers actions for the currently open rev-239 gameframe. */
internal fun playerButtonActions(
    movement: PlayerMovement,
    varps: PlayerVarps,
    logout: PlayerLogoutState,
): ButtonActionRegistry = buttonActions {
    val toggleRun = ButtonAction { click ->
        if (click.op != 1) return@ButtonAction
        val enabled = varps[PlayerVarpCatalog.RUN_MODE] != 1
        varps[PlayerVarpCatalog.RUN_MODE] = if (enabled) 1 else 0
        movement.runEnabled = enabled
    }
    onButton(interfaceId = 116, componentId = 30, toggleRun)
    onButton(interfaceId = 160, componentId = 28, toggleRun)
    onButton(interfaceId = 182, componentId = 8) { click ->
        if (click.op == 1) logout.request()
    }
}
