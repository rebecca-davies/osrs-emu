package emu.game.content.areas.inferno

import emu.game.map.Tile

/** Revision-pinned locations and loc type used by the Inferno free-mode beta flow. */
data class InfernoFreeModeConfig(
    val challengePortalType: Int,
    val clanWarsArrival: Tile,
    val arenaArrival: Tile,
) {
    init {
        require(challengePortalType in 0..0xFFFF) { "challenge portal type must fit an unsigned short" }
    }
}
