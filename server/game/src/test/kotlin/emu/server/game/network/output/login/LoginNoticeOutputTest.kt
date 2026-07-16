package emu.server.game.network.output.login

import emu.game.content.player.login.LoginNotice
import emu.protocol.osrs239.game.message.chat.MessageGame
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginNoticeOutputTest {
    @Test
    fun `game login notices map to revision 239 game messages`() {
        assertEquals(
            listOf(MessageGame(MessageGame.GAME_MESSAGE, "A content-owned notice.")),
            LoginNoticeOutput.messages(listOf(LoginNotice("A content-owned notice."))),
        )
    }
}
