package emu.server.world.player

import emu.game.script.PlayerScriptRunner
import emu.game.script.ServerTriggerType
import emu.server.world.entity.WorldPlayer

/** Runs world-owned interface and player-lifecycle triggers at their authoritative phase. */
class PlayerTriggerProcess(private val runner: PlayerScriptRunner) {
    internal fun closeModal(player: WorldPlayer) {
        player.interfaces.closeModal()
        processInterfaceCloses(player)
    }

    internal fun processInterfaceCloses(player: WorldPlayer) {
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
