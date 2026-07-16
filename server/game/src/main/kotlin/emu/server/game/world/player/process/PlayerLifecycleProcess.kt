package emu.server.game.world.player.process

import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.CharacterWriteState
import emu.server.game.persistence.CharacterWriteBackException
import emu.server.game.world.World
import emu.server.game.world.player.ConnectedPlayer

/** Owns LOGIN world entry and non-blocking LOGOUT snapshot write-back on the world thread. */
class PlayerLifecycleProcess(
    private val writes: CharacterWriteQueue,
    private val triggers: PlayerTriggerProcess,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    internal fun processLogout(connected: ConnectedPlayer) {
        val player = connected.player
        val writeBack = connected.writeBack
        if (!player.logoutRequested && !player.loggingOut) return
        player.beginLogout()
        triggers.closeModal(player)
        player.actionQueue.clearWeak()
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
            triggers.logout(player)
        }
        val snapshot = writeBack.snapshot(player, nanoTime())
        writeBack.completion = writes.submit(snapshot)
    }

    internal fun enterPendingPlayers(world: World) = world.enterPendingPlayers()

    internal fun processLogin(world: World, connected: ConnectedPlayer) =
        world.activate(connected, triggers::login)

    internal fun forceSnapshot(connected: ConnectedPlayer) {
        val player = connected.player
        val writeBack = connected.writeBack
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
