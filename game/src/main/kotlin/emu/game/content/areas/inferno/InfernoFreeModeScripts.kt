package emu.game.content.areas.inferno

import emu.game.script.content.PlayerContent

/** Registers the Inferno editor lifecycle and the hub/challenge portal transitions. */
object InfernoFreeModeScripts {
    fun register(
        content: PlayerContent,
        arena: InfernoArena,
        editor: InfernoEditorInterface,
    ) {
        content.onLogin {
            arena.enterHub(player)
            editor.restoreQuestTab(player)
        }
        content.onLogout {
            arena.release(player)
        }
        content.onLoc1(arena.config.challengePortalType) {
            val state = arena.enter(player)
            editor.openLauncher(player, state)
            player.messageGame("Inferno free mode started empty and paused.")
            player.messageGame("Use the Inferno Editor quest tab to configure the arena.")
        }
        content.onLoc1(arena.config.exitPortalType) {
            editor.close(player)
            arena.enterHub(player)
            player.messageGame("You leave the Inferno.")
        }
    }
}
