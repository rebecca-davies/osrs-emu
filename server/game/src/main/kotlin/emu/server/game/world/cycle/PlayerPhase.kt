package emu.server.game.world.cycle

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRequest
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.timer.PlayerTimerRunner
import emu.server.game.world.player.script.runInterfaceCloseTriggers

/** Runs the content-owned portion of one player's authoritative phase in RuneScape order. */
class PlayerPhase(private val scripts: PlayerScriptRunner) {
    private val timers = PlayerTimerRunner(scripts, scripts::runInterfaceCloseTriggers)

    internal fun begin(worldTick: Long) = scripts.beginCycle(worldTick)

    internal fun run(player: Player) {
        scripts.resume(player)
        scripts.runInterfaceCloseTriggers(player)
        player.closeRequestedModal()
        player.runPrimaryAndWeakActions()
        timers.run(player)
        player.runEngineActions()
    }

    private fun Player.closeRequestedModal() {
        if (!consumeModalCloseRequest()) return
        closeModal()
        scripts.runInterfaceCloseTriggers(this)
    }

    private fun Player.runPrimaryAndWeakActions() {
        if (actionQueue.primarySize == 0 && actionQueue.weakSize == 0) return
        actionQueue.processPrimaryAndWeak(
            canAccess = ::canAccess,
            closeModal = {
                closeModal()
                scripts.runInterfaceCloseTriggers(this)
            },
            loggingOut = loggingOut,
        ) {
            run(it)
            scripts.runInterfaceCloseTriggers(this)
        }
    }

    private fun Player.runEngineActions() {
        if (actionQueue.engineSize == 0) return
        actionQueue.processEngine(::canAccess) {
            run(it)
            scripts.runInterfaceCloseTriggers(this)
        }
    }

    private fun Player.run(request: PlayerScriptRequest) {
        scripts.start(this, request.script, argument = request.argument)
    }
}
