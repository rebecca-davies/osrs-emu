package emu.server.game.world.player

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.CharacterWriteState
import emu.server.game.persistence.CharacterWriteBackException
import emu.server.game.world.World
import emu.server.game.world.player.script.runInterfaceCloseTriggers
import emu.server.game.world.player.script.runLoginTrigger
import emu.server.game.world.player.script.runLogoutTrigger

/** Coordinates player login, logout triggers, and non-blocking durable write-back. */
class PlayerLifecycle(
    private val world: World,
    private val writes: CharacterWriteQueue,
    private val scripts: PlayerScriptRunner,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    internal fun enterPendingPlayers() = world.enterPendingPlayers()

    internal fun login(player: Player) = world.activate(player, scripts::runLoginTrigger)

    internal fun logout(player: Player) {
        val writeBack = world.writeBack(player)
        if (!player.logoutRequested && !player.idleLogoutRequested && !player.loggingOut) return
        player.beginLogout()
        player.closeModal()
        scripts.runInterfaceCloseTriggers(player)
        when (writeBack.completion?.state()) {
            CharacterWriteState.PENDING,
            CharacterWriteState.DURABLE -> return
            CharacterWriteState.FAILED -> throw CharacterWriteBackException(player.id)
            null -> Unit
        }
        if (!player.canAccess() || player.actionQueue.engineSize > 0) return
        if (!player.actionQueue.isDiscardableForLogout()) return
        if (!writeBack.logoutTriggerStarted) {
            writeBack.logoutTriggerStarted = true
            scripts.runLogoutTrigger(player)
        }
        val snapshot = writeBack.snapshot(player, nanoTime())
        writeBack.completion = writes.submit(snapshot)
    }

    internal fun forceSnapshot(player: Player) {
        val writeBack = world.writeBack(player)
        when (writeBack.completion?.state()) {
            CharacterWriteState.DURABLE,
            CharacterWriteState.PENDING -> return
            CharacterWriteState.FAILED -> throw CharacterWriteBackException(player.id)
            null -> Unit
        }
        player.beginLogout()
        val snapshot = writeBack.snapshot(player, nanoTime())
        writeBack.completion = writes.submit(snapshot) ?: throw CharacterWriteBackException(player.id)
    }
}
