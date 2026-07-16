package emu.server.game.persistence

import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.server.game.world.player.WorldPlayer
import emu.server.session.account.AccountPrivilege
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerWriteBackTest {
    @Test
    fun `play time saturates when a loaded account is near the storage limit`() {
        val record =
            CharacterRecord(
                1,
                "Player1",
                CharacterPosition(3_200, 3_200, 0),
                Long.MAX_VALUE - 1,
            )
        val writeBack = PlayerWriteBack(record, sessionStartedNanos = 0)

        val save =
            writeBack.snapshot(
                WorldPlayer(record, AccountPrivilege.PLAYER),
                nowNanos = 10_000_000_000,
            )

        assertEquals(Long.MAX_VALUE, save.playTimeSeconds)
    }
}
