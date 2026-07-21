package emu.server.game.world.player.cheat

import emu.server.session.account.AccountPrivilege

/** Starts bounded localhost bot clients for administrators. */
internal class AddBotCheatHandler(
    private val bots: BotClientRequestSink,
) : PlayerCheatHandler {
    override fun execute(privilege: AccountPrivilege, arguments: String): String? {
        if (privilege != AccountPrivilege.ADMINISTRATOR) return null
        val count = arguments.toIntOrNull()
            ?: return "Usage: ::addbot <count>."
        return when (val result = bots.add(count)) {
            is BotClientRequestResult.Accepted ->
                "Starting ${result.count} bot client(s); ${result.reservedClients} slot(s) reserved."
            is BotClientRequestResult.InvalidCount ->
                "Bot count must be between 1 and ${result.maximum}."
            BotClientRequestResult.CapacityReached -> "The configured bot client limit has been reached."
            BotClientRequestResult.Busy -> "The bot client request queue is busy; try again shortly."
            BotClientRequestResult.Unavailable -> "The bot client service is unavailable."
        }
    }
}
