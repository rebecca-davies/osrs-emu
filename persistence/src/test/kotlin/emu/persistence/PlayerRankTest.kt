package emu.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerRankTest {
    @Test fun `database ids map to stable player moderator and administrator ranks`() {
        assertEquals(PlayerRank.PLAYER, PlayerRank.fromId(0))
        assertEquals(PlayerRank.MODERATOR, PlayerRank.fromId(1))
        assertEquals(PlayerRank.ADMINISTRATOR, PlayerRank.fromId(2))
        assertFailsWith<IllegalArgumentException> { PlayerRank.fromId(3) }
    }
}
