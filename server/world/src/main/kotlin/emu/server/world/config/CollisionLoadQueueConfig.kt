package emu.server.world.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Bounds off-thread cache collision preparation and shutdown. */
data class CollisionLoadQueueConfig(
    val capacity: Int = 128,
    val workerThreads: Int = 2,
    val shutdownTimeout: Duration = 2.seconds,
) {
    init {
        require(capacity > 0) { "collision load queue capacity must be positive" }
        require(workerThreads > 0) { "collision load worker count must be positive" }
        require(shutdownTimeout.isPositive()) { "collision load shutdown timeout must be positive" }
    }
}
