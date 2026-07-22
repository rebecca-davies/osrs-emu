package emu.game.content.areas.inferno

import emu.game.script.content.PlayerContent

/** Clan Wars portal content entering one character's empty Inferno free-mode instance. */
object InfernoFreeModeScripts {
    fun register(content: PlayerContent, arena: InfernoArena) {
        content.onLogin {
            arena.enterHub(player)
        }
        content.onLoc1(arena.config.challengePortalType) {
            arena.enter(player)
            player.messageGame("Inferno free mode started empty and paused.")
            player.messageGame("Equipment tab: choose gear, place NPCs, pause, or clear the arena.")
        }
    }
}
