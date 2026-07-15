package emu.server.host

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Timeout for reserving a character slot before login success is written. */
data class CoordinatorConfig(val admissionTimeout: Duration = 15.seconds) {
    init {
        require(admissionTimeout.isPositive()) { "coordinator admission timeout must be positive" }
    }
}
