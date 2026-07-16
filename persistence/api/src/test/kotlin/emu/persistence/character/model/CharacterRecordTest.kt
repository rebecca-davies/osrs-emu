package emu.persistence.character.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CharacterRecordTest {
    @Test
    fun `loaded character identity and play time must be valid`() {
        val position = CharacterPosition(3_200, 3_200, 0)

        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(0, "Player", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(1, "", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(1, "NameTooLong12", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(1, "bad\u0000name", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(1, "bad\uD83D\uDE00", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CharacterRecord(1, "Player", position, -1)
        }
    }
}
