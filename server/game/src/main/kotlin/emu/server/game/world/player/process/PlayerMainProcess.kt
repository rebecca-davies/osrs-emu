package emu.server.game.world.player.process

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRequest
import emu.game.script.execution.PlayerScriptRunner
import emu.server.game.world.player.WorldPlayer

/** Runs one player's authoritative main phase in RuneScape order. */
class PlayerMainProcess(
    private val runner: PlayerScriptRunner,
    private val triggers: PlayerTriggerProcess,
    private val movement: PlayerMovementCycleProcess,
) {
    internal fun beginCycle(worldTick: Long) = runner.beginCycle(worldTick)

    internal fun process(player: WorldPlayer) {
        runner.resume(player)
        triggers.processInterfaceCloses(player)
        processPrimaryAndWeakQueues(player)
        processEngineQueue(player)
        movement.process(player.movement)
    }

    private fun processPrimaryAndWeakQueues(player: WorldPlayer) {
        player.actionQueue.processPrimaryAndWeak(
            canAccess = player::canAccess,
            closeModal = { triggers.closeModal(player) },
            loggingOut = player.loggingOut,
        ) {
            execute(player, it)
            triggers.processInterfaceCloses(player)
        }
    }

    private fun processEngineQueue(player: WorldPlayer) {
        player.actionQueue.processEngine(player::canAccess) {
            execute(player, it)
            triggers.processInterfaceCloses(player)
        }
    }

    private fun execute(player: Player, request: PlayerScriptRequest) {
        runner.start(player, request.script, argument = request.argument)
    }
}
