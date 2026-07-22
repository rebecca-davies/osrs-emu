package emu.server.game.world.player.command.bot

import emu.game.map.Tile
import emu.game.player.Player
import emu.server.game.world.player.command.buildPlayerCommandRepository
import emu.server.session.account.AccountPrivilege
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddBotsCommandTest {
    private val player = Player(Tile(3_222, 3_218))

    @Test
    fun `administrator command submits the requested bounded client count`() {
        var requested = 0
        val commands =
            buildPlayerCommandRepository { count ->
                requested = count
                BotClientRequestResult.Accepted(count, reservedClients = count + 1)
            }

        val response = commands.execute("  AdDbOtS   4  ", player, AccountPrivilege.ADMINISTRATOR)

        assertEquals(4, requested)
        assertEquals("Starting 4 automated player client(s); 5 slot(s) reserved.", response)
    }

    @Test
    fun `declarative administrator role prevents lower roles reaching the bot service`() {
        var requested = false
        val commands =
            buildPlayerCommandRepository {
                requested = true
                BotClientRequestResult.Accepted(it, it)
            }

        val response = commands.execute("addbots 4", player, AccountPrivilege.MODERATOR)

        assertNull(response)
        assertEquals(false, requested)
    }
}
