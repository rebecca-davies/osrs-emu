package emu.server.world.config

import emu.game.input.PlayerInputQueueConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Resource and timeout policy applied to each admitted game connection. */
data class GameConnectionConfig(
    val idleTimeout: Duration = 30.seconds,
    val inputQueue: PlayerInputQueueConfig = PlayerInputQueueConfig(),
) {
    init {
        require(idleTimeout.isPositive()) { "game idle timeout must be positive" }
    }
}
