package emu.game.content.areas.inferno

import emu.game.map.Tile

/** Revision-pinned locations and loc type used by the Inferno free-mode beta flow. */
data class InfernoFreeModeConfig(
    val challengePortalType: Int,
    val clanWarsArrival: Tile,
    val arenaArrival: Tile,
    val arenaBounds: InfernoArenaBounds,
    val maxNpcs: Int,
) {
    init {
        require(challengePortalType in 0..0xFFFF) { "challenge portal type must fit an unsigned short" }
        require(maxNpcs > 0) { "maximum NPC count must be positive" }
        require(arenaBounds.contains(arenaArrival)) { "Inferno arrival must be inside the arena bounds" }
    }
}

/** Inclusive rectangular placement bounds for the Inferno arena. */
data class InfernoArenaBounds(
    val southWest: Tile,
    val northEast: Tile,
) {
    init {
        require(southWest.plane == northEast.plane) { "Inferno bounds must occupy one plane" }
        require(southWest.x <= northEast.x && southWest.y <= northEast.y) {
            "Inferno bounds must run from south-west to north-east"
        }
    }

    /** Whether a square footprint is completely inside these bounds. */
    fun contains(position: Tile, size: Int = 1): Boolean =
        size > 0 && position.plane == southWest.plane &&
            position.x >= southWest.x && position.y >= southWest.y &&
            position.x + size - 1 <= northEast.x && position.y + size - 1 <= northEast.y
}
