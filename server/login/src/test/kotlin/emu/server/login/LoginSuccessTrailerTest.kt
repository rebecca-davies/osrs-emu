package emu.server.login

import emu.server.login.wire.loginSuccessTrailer
import emu.server.session.account.AccountPrivilege
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the login-info trailer's shape against the decompiled rev-239 login state:
 * `[1 advertised span=37][34-byte account-info payload]`,
 * with the 2-byte `di` (local player index) field at offset 7 within the block set to
 * the attached world's player index and every other account-info byte zero. The first game's three-byte
 * REBUILD_NORMAL header completes the advertised 37-byte span.
 */
class LoginSuccessTrailerTest {
    @Test fun `trailer advertises account info plus the first game header without padding it`() {
        val trailer = loginSuccessTrailer(PLAYER_RIGHTS, playerIndex = 1)
        assertEquals(35, trailer.size)
        assertEquals(37, trailer[0].toInt() and 0xFF)
        assertEquals(37, trailer.size - 1 + 3)
    }

    @Test fun `di field encodes the attached player index big-endian`() {
        val trailer = loginSuccessTrailer(PLAYER_RIGHTS, playerIndex = 1_736)
        // Block starts at trailer index 1 (after the length byte); di is at block offset 7 -> trailer index 8.
        val diHi = trailer[8].toInt() and 0xFF
        val diLo = trailer[9].toInt() and 0xFF
        assertEquals(1_736, (diHi shl 8) or diLo)
    }

    @Test fun `every byte outside the di field is zero`() {
        val trailer = loginSuccessTrailer(PLAYER_RIGHTS, playerIndex = 2_047)
        for (i in 1 until trailer.size) {
            if (i == 8 || i == 9) continue
            assertEquals(0, trailer[i].toInt(), "trailer byte $i expected zero")
        }
    }

    @Test fun `player index must fit a non-zero rev-239 player slot`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            loginSuccessTrailer(PLAYER_RIGHTS, playerIndex = 0)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            loginSuccessTrailer(PLAYER_RIGHTS, playerIndex = 2_048)
        }
    }
}

private val PLAYER_RIGHTS = AccountPrivilege.PLAYER
