package emu.persistence.character

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlayerRecordTest {
    @Test
    fun `loaded character identity and play time must be valid`() {
        val position = PlayerPosition(3_200, 3_200, 0)

        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(0, "Player", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(1, "", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(1, "NameTooLong12", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(1, "bad\u0000name", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(1, "bad\uD83D\uDE00", position, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerRecord(1, "Player", position, -1)
        }
    }
}
