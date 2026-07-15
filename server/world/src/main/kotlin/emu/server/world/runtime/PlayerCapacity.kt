package emu.server.world.runtime

/** Rev-239 player-index capacity enforced by the authoritative world. */
object PlayerCapacity {
    /** Mutually visible player indices `1..2047`; index `0` is excluded by other-player GPI. */
    const val PER_WORLD = 2_047
}
