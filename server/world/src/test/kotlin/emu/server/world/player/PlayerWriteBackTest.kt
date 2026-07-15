package emu.server.world.player

import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.server.world.runtime.PlayerWriteBack
import emu.server.world.entity.WorldPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerWriteBackTest {
    @Test
    fun `play time saturates when a loaded account is near the storage limit`() {
        val record =
            PlayerRecord(
                1,
                "Player1",
                PlayerPosition(3_200, 3_200, 0),
                Long.MAX_VALUE - 1,
            )
        val writeBack = PlayerWriteBack(record, sessionStartedNanos = 0)

        val save = writeBack.snapshot(WorldPlayer(record), nowNanos = 10_000_000_000)

        assertEquals(Long.MAX_VALUE, save.playTimeSeconds)
    }
}
