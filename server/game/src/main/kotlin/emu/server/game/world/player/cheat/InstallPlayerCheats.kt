package emu.server.game.world.player.cheat

/** Builds the immutable set of server-owned player cheats. */
fun buildPlayerCheatRepository(bots: BotClientRequestSink): PlayerCheatRepository =
    PlayerCheatRepositoryBuilder()
        .bind("addbot", AddBotCheatHandler(bots))
        .build()
