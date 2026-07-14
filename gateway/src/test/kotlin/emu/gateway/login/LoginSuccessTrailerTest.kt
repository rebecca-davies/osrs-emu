package emu.gateway.login

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the login-info trailer's shape against the decompiled rev-239 login state:
 * `[1 advertised span=37][34-byte account-info payload]`,
 * with the 2-byte `di` (local player index) field at offset 7 within the block set to
 * [LOCAL_PLAYER_INDEX] and every other account-info byte zero. The first game's three-byte
 * REBUILD_NORMAL header completes the advertised 37-byte span.
 */
class LoginSuccessTrailerTest {
    @Test fun `trailer advertises account info plus the first game header without padding it`() {
        assertEquals(35, LOGIN_SUCCESS_TRAILER.size)
        assertEquals(37, LOGIN_SUCCESS_TRAILER[0].toInt() and 0xFF)
        assertEquals(37, LOGIN_SUCCESS_TRAILER.size - 1 + 3)
    }

    @Test fun `di field (2 bytes at block offset 7) encodes LOCAL_PLAYER_INDEX big-endian`() {
        // Block starts at trailer index 1 (after the length byte); di is at block offset 7 -> trailer index 8.
        val diHi = LOGIN_SUCCESS_TRAILER[8].toInt() and 0xFF
        val diLo = LOGIN_SUCCESS_TRAILER[9].toInt() and 0xFF
        assertEquals(LOCAL_PLAYER_INDEX, (diHi shl 8) or diLo)
    }

    @Test fun `every byte outside the di field is zero`() {
        for (i in 1 until LOGIN_SUCCESS_TRAILER.size) {
            if (i == 8 || i == 9) continue
            assertEquals(0, LOGIN_SUCCESS_TRAILER[i].toInt(), "trailer byte $i expected zero")
        }
    }
}
