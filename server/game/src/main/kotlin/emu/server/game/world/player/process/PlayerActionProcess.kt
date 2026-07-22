package emu.server.game.world.player.process

import emu.game.action.PlayerAction
import emu.game.map.Tile
import emu.game.pathfinding.route.PlayerRouteFinder
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.server.game.network.connection.PlayerConnection
import emu.server.game.world.player.WorldPlayer
import emu.server.game.world.player.cheat.PlayerCheatRepository

/** Drains decoded client actions for every player during the global client-input phase. */
class PlayerActionProcess(
    private val routeFinder: PlayerRouteFinder,
    private val chat: PlayerChatActionProcess,
    private val runner: PlayerScriptRunner,
    private val cheats: PlayerCheatRepository,
) {
    internal fun process(player: WorldPlayer, connection: PlayerConnection) {
        connection.actions.drain { action ->
            when (action) {
                is PlayerAction.Route -> connection.pendingRoute = action
                is PlayerAction.Button -> button(player, action.click)
                is PlayerAction.Chat -> chat.process(player, connection.publicChat, action.input)
                is PlayerAction.Cheat -> {
                    val response = cheats.execute(action.input.text, player.privilege)
                    if (response != null) connection.queueGameMessage(response)
                }
                PlayerAction.CloseModal -> player.requestModalClose()
                PlayerAction.IdleLogout -> player.requestIdleLogout()
            }
        }
        val route = connection.pendingRoute
        if (!player.logoutRequested && route != null) {
            connection.pendingRoute = null
            route(player, route)
        }
    }

    private fun button(player: WorldPlayer, click: ButtonClick) {
        if (!player.interfaces.isVisible(click.component)) return
        runner.trigger(
            player,
            ServerTriggerType.IF_BUTTON,
            subject = click.packedComponent,
            lastButton = click,
        )
    }

    private fun route(player: WorldPlayer, action: PlayerAction.Route) {
        val state = player.movement
        val destination = Tile(action.x, action.y, state.position.plane)
        val temporaryRun = if (action.invertRun) !state.runEnabled else null
        routeFinder.routeTo(state, destination, temporaryRun)
    }
}
