package emu.server.bot.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MAX_SUPPORTED_CLIENTS = 1_000
private const val DEFAULT_CONCURRENT_LOGINS = 32

/** Bounded localhost policy for up to one thousand generated headless clients. */
data class BotConfig(
    val maxClients: Int = MAX_SUPPORTED_CLIENTS,
    val maxPerRequest: Int = maxClients,
    val requestQueueCapacity: Int = 8,
    val maxConcurrentLogins: Int = DEFAULT_CONCURRENT_LOGINS,
    val workerThreads: Int = 2,
    val loginTimeout: Duration = 15.seconds,
    val movement: BotMovementConfig = BotMovementConfig(),
) {
    init {
        require(maxClients in 1..MAX_SUPPORTED_CLIENTS) {
            "bot client limit must be between 1 and $MAX_SUPPORTED_CLIENTS"
        }
        require(maxPerRequest in 1..maxClients) { "bot request limit must be between 1 and maxClients" }
        require(requestQueueCapacity > 0) { "bot request queue capacity must be positive" }
        require(maxConcurrentLogins > 0) { "concurrent bot login limit must be positive" }
        require(workerThreads > 0) { "bot worker count must be positive" }
        require(loginTimeout.isPositive()) { "bot login timeout must be positive" }
    }
}

/** Movement area and cadence used by each connected bot client. */
data class BotMovementConfig(
    val centreX: Int = 3_222,
    val centreZ: Int = 3_218,
    val radius: Int = 6,
    val interval: Duration = 3.seconds,
) {
    init {
        require(interval >= 1.milliseconds) { "bot movement interval must be at least one millisecond" }
        require(radius in 1..MAX_RADIUS) { "bot movement radius must be between 1 and $MAX_RADIUS" }
        require(centreX in radius..MAX_COORDINATE - radius) {
            "bot movement x range must fit the 14-bit world coordinate"
        }
        require(centreZ in radius..MAX_COORDINATE - radius) {
            "bot movement z range must fit the 14-bit world coordinate"
        }
    }

    private companion object {
        const val MAX_COORDINATE = 0x3FFF
        const val MAX_RADIUS = 32
    }
}
