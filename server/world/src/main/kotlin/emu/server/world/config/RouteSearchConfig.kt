package emu.server.world.config

/** Hard upper bound on full 128x128 path searches performed by one authoritative world cycle. */
data class RouteSearchConfig(val maxPerCycle: Int = 32) {
    init {
        require(maxPerCycle > 0) { "route search cycle limit must be positive" }
    }
}
