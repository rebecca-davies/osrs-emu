package emu.server.world.runtime

import emu.persistence.character.CharacterWriteCompletion
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.character.PlayerChatFiltersRecord
import emu.server.world.entity.WorldPlayer

/** Snapshot and completion state for one player's durable session write-back. */
internal class PlayerWriteBack(
    record: PlayerRecord,
    private val sessionStartedNanos: Long = System.nanoTime(),
) {
    private val loadedPlayTimeSeconds = record.playTimeSeconds
    private var snapshot: PlayerSessionSave? = null

    var completion: CharacterWriteCompletion? = null
    var logoutTriggerStarted: Boolean = false

    val durable: Boolean
        get() = completion?.isDurable() == true

    val snapshotTaken: Boolean
        get() = snapshot != null

    fun snapshot(player: WorldPlayer, nowNanos: Long): PlayerSessionSave =
        snapshot
            ?: PlayerSessionSave(
                playerId = player.id,
                position =
                    PlayerPosition(
                        player.movement.position.x,
                        player.movement.position.y,
                        player.movement.position.plane,
                    ),
                playTimeSeconds = totalPlayTime(elapsedSeconds(nowNanos)),
                dirtyVarps = player.varps.dirtyPersistentValues(),
                chatFilters =
                    PlayerChatFiltersRecord(
                        player.chatFilters.publicMode,
                        player.chatFilters.privateMode,
                        player.chatFilters.tradeMode,
                    ),
            ).also { snapshot = it }

    private fun elapsedSeconds(nowNanos: Long): Long =
        ((nowNanos - sessionStartedNanos) / NANOS_PER_SECOND).coerceAtLeast(0)

    private fun totalPlayTime(elapsedSeconds: Long): Long =
        if (loadedPlayTimeSeconds > Long.MAX_VALUE - elapsedSeconds) {
            Long.MAX_VALUE
        } else {
            loadedPlayTimeSeconds + elapsedSeconds
        }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
