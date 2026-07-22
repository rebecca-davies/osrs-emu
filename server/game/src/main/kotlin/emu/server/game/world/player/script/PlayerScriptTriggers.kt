package emu.server.game.world.player.script

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType

internal fun PlayerScriptRunner.runInterfaceCloseTriggers(player: Player) {
    while (true) {
        val interfaceId = player.interfaces.pollCloseTrigger() ?: return
        trigger(player, ServerTriggerType.IF_CLOSE, interfaceId, protect = false)
    }
}

internal fun PlayerScriptRunner.runLoginTrigger(player: Player) {
    trigger(player, ServerTriggerType.LOGIN)
}

internal fun PlayerScriptRunner.runLogoutTrigger(player: Player) {
    try {
        trigger(player, ServerTriggerType.LOGOUT)
    } finally {
        player.discardActiveScript()
    }
}
