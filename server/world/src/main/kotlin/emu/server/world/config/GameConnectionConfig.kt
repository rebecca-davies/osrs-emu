package emu.server.world.config

import emu.game.action.GameInputQueueConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Resource and timeout policy applied to each attached game connection. */
data class GameConnectionConfig(
    val idleTimeout: Duration = 30.seconds,
    val inputQueue: GameInputQueueConfig = GameInputQueueConfig(),
    val outputQueueCapacity: Int = 4,
) {
    init {
        require(idleTimeout.isPositive()) { "game idle timeout must be positive" }
        require(outputQueueCapacity > 0) { "game output queue capacity must be positive" }
    }
}
