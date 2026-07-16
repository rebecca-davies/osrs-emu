package emu.server.game.world.player.route

import emu.server.game.config.RouteSearchConfig

/** World-thread-owned deterministic allowance for expensive path searches in one cycle. */
class RouteSearchBudget(config: RouteSearchConfig) {
    private val maximum = config.maxPerCycle
    private var remaining = maximum

    internal fun beginCycle() {
        remaining = maximum
    }

    internal fun acquire(): Boolean {
        if (remaining == 0) return false
        remaining--
        return true
    }
}
