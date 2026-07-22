package emu.server.game.world.player.command

import emu.server.game.world.player.command.bot.AddBotsCommand
import emu.server.game.world.player.command.bot.BotClientRequestSink

/** Builds the immutable set of server-owned player commands. */
fun buildPlayerCommandRepository(bots: BotClientRequestSink): PlayerCommandRepository =
    PlayerCommandRepositoryBuilder()
        .bind(AddBotsCommand(bots))
        .build()
