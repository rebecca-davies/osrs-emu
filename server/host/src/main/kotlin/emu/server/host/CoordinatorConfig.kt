package emu.server.host

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Timeout for preparing world entry before login success is written. */
data class CoordinatorConfig(val worldEntryTimeout: Duration = 15.seconds) {
    init {
        require(worldEntryTimeout.isPositive()) { "coordinator world-entry timeout must be positive" }
    }
}
