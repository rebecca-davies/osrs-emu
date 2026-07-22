package emu.server.game.world.player.command.bot

import emu.game.player.Player
import emu.server.game.world.player.command.Command
import emu.server.game.world.player.command.PlayerCommand
import emu.server.game.world.player.command.Role
import emu.server.session.account.AccountPrivilege.ADMINISTRATOR

/** Starts bounded automated player clients through the host-owned bot service. */
@Command("addbots")
@Role(ADMINISTRATOR)
internal class AddBotsCommand(
    private val bots: BotClientRequestSink,
) : PlayerCommand {
    override fun execute(player: Player, arguments: String): String? {
        val count = arguments.toIntOrNull()
            ?: return "Usage: ::addbots <count>."
        return when (val result = bots.add(count)) {
            is BotClientRequestResult.Accepted ->
                "Starting ${result.count} automated player client(s); " +
                    "${result.reservedClients} slot(s) reserved."
            is BotClientRequestResult.InvalidCount ->
                "Bot count must be between 1 and ${result.maximum}."
            BotClientRequestResult.CapacityReached -> "The configured bot client limit has been reached."
            BotClientRequestResult.Busy -> "The bot client request queue is busy; try again shortly."
            BotClientRequestResult.Unavailable -> "The bot client service is unavailable."
        }
    }
}
