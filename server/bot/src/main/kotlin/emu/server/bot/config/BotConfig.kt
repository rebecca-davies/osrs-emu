package emu.server.bot.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Bounded localhost policy for generated headless clients. */
data class BotConfig(
    val maxClients: Int = 128,
    val maxPerRequest: Int = 32,
    val requestQueueCapacity: Int = 8,
    val maxConcurrentLogins: Int = 4,
    val workerThreads: Int = 2,
    val loginTimeout: Duration = 15.seconds,
    val keepAliveInterval: Duration = 10.seconds,
) {
    init {
        require(maxClients > 0) { "bot client limit must be positive" }
        require(maxPerRequest in 1..maxClients) { "bot request limit must be between 1 and maxClients" }
        require(requestQueueCapacity > 0) { "bot request queue capacity must be positive" }
        require(maxConcurrentLogins > 0) { "concurrent bot login limit must be positive" }
        require(workerThreads > 0) { "bot worker count must be positive" }
        require(loginTimeout.isPositive()) { "bot login timeout must be positive" }
        require(keepAliveInterval.isPositive()) { "bot keep-alive interval must be positive" }
    }
}
