package emu.game.content.player.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginNoticeTest {
    @Test
    fun `standard login content owns the RuneScape welcome text`() {
        assertEquals(listOf(LoginNotice("Welcome to RuneScape.")), LoginNotices.ALL)
    }

    @Test
    fun `login notices reject text that cannot be sent as a protocol string`() {
        assertFailsWith<IllegalArgumentException> { LoginNotice(" ") }
        assertFailsWith<IllegalArgumentException> { LoginNotice("invalid\u0000message") }
    }
}
