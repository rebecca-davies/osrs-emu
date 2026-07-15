package emu.server.world.player

import emu.game.player.Player
import emu.game.script.PlayerScriptRequest
import emu.game.script.PlayerScriptRunner
import emu.server.world.entity.WorldPlayer

/** Resumes protected scripts and processes player action queues in authentic order. */
class PlayerScriptProcess(
    private val runner: PlayerScriptRunner,
    private val triggers: PlayerTriggerProcess,
) {
    internal fun beginCycle(worldTick: Long) = runner.beginCycle(worldTick)

    internal fun process(player: WorldPlayer) {
        runner.resume(player)
        triggers.processInterfaceCloses(player)
        player.actionQueue.processPrimaryAndWeak(
            canAccess = player::canAccess,
            closeModal = { triggers.closeModal(player) },
            loggingOut = player.loggingOut,
        ) {
            execute(player, it)
            triggers.processInterfaceCloses(player)
        }
        player.actionQueue.processEngine(player::canAccess) {
            execute(player, it)
            triggers.processInterfaceCloses(player)
        }
    }

    private fun execute(player: Player, request: PlayerScriptRequest) {
        runner.start(player, request.script, argument = request.argument)
    }
}
