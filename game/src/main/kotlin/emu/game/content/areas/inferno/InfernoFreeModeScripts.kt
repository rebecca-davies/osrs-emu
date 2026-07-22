package emu.game.content.areas.inferno

import emu.game.map.MapInstance
import emu.game.script.content.PlayerContent

/** Clan Wars portal content entering one character's empty Inferno free-mode instance. */
object InfernoFreeModeScripts {
    fun register(content: PlayerContent, config: InfernoFreeModeConfig) {
        content.onLogin {
            player.teleportTo(config.clanWarsArrival, MapInstance.SHARED)
        }
        content.onLoc1(config.challengePortalType) {
            player.teleportTo(config.arenaArrival, MapInstance.privateTo(player.id))
            player.messageGame("Inferno free mode started. The arena is empty.")
        }
    }
}
