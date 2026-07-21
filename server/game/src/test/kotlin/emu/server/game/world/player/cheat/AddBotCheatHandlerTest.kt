package emu.server.game.world.player.cheat

import emu.server.session.account.AccountPrivilege
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddBotCheatHandlerTest {
    @Test
    fun `administrator command submits the requested bounded client count`() {
        var requested = 0
        val cheats =
            buildPlayerCheatRepository { count ->
                requested = count
                BotClientRequestResult.Accepted(count, reservedClients = count + 1)
            }

        val response = cheats.execute("  AdDbOt   4  ", AccountPrivilege.ADMINISTRATOR)

        assertEquals(4, requested)
        assertEquals("Starting 4 bot client(s); 5 slot(s) reserved.", response)
    }

    @Test
    fun `non-administrator command cannot reach the bot service`() {
        var requested = false
        val cheats =
            buildPlayerCheatRepository {
                requested = true
                BotClientRequestResult.Accepted(it, it)
            }

        val response = cheats.execute("addbot 4", AccountPrivilege.MODERATOR)

        assertNull(response)
        assertEquals(false, requested)
    }
}
