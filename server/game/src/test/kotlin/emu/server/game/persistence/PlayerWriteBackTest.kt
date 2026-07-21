package emu.server.game.persistence

import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.server.game.world.player.WorldPlayer
import emu.server.session.account.AccountPrivilege
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerWriteBackTest {
    @Test
    fun `write back retains the character appearance loaded for the session`() {
        val appearance =
            CharacterAppearance(
                CharacterGender.FEMALE,
                CharacterBodyKits(hair = 55, jaw = 306, torso = 60, arms = 66, hands = 68, legs = 78, feet = 80),
                CharacterColors(hair = 29, torso = 28, legs = 27, feet = 5, skin = 13),
            )
        val record =
            CharacterRecord(
                1,
                "Player1",
                CharacterPosition(3_200, 3_200, 0),
                0,
                appearance = appearance,
            )

        val save =
            PlayerWriteBack(record, sessionStartedNanos = 0).snapshot(
                WorldPlayer(record, AccountPrivilege.PLAYER),
                nowNanos = 0,
            )

        assertEquals(appearance, save.appearance)
    }

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
