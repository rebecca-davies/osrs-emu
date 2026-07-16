package emu.server.game.config

import emu.game.action.IncomingPlayerActionQueueConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Resource and timeout policy applied to each attached game connection. */
data class GameConnectionConfig(
    val idleTimeout: Duration = 30.seconds,
    val incomingActions: IncomingPlayerActionQueueConfig = IncomingPlayerActionQueueConfig(),
    val outputQueueCapacity: Int = 4,
) {
    init {
        require(idleTimeout.isPositive()) { "game idle timeout must be positive" }
        require(outputQueueCapacity > 0) { "game output queue capacity must be positive" }
    }
}
