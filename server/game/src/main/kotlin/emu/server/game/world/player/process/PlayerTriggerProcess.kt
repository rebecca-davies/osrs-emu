package emu.server.game.world.player.process

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType
import emu.server.game.world.player.WorldPlayer

/** Runs world-owned interface and player-lifecycle triggers at their authoritative phase. */
class PlayerTriggerProcess(private val runner: PlayerScriptRunner) {
    internal fun processInterfaceCloses(player: Player) {
        while (true) {
            val interfaces = player.interfaces.drainCloseTriggers()
            if (interfaces.isEmpty()) return
            interfaces.forEach { runner.trigger(player, ServerTriggerType.IF_CLOSE, it, protect = false) }
        }
    }

    internal fun login(player: WorldPlayer) {
        runner.trigger(player, ServerTriggerType.LOGIN)
    }

    internal fun logout(player: WorldPlayer) {
        try {
            runner.trigger(player, ServerTriggerType.LOGOUT)
        } finally {
            runner.discard(player)
        }
    }
}
