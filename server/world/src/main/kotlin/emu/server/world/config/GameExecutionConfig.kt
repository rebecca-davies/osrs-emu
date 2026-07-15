package emu.server.world.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_GAME_IO_WORKERS = 8
private const val DEFAULT_GAME_SESSIONS = 2_047

/** Independent limits for game connection IO and authoritative world execution. */
data class GameExecutionConfig(
    val ioWorkerThreads: Int = DEFAULT_GAME_IO_WORKERS,
    val maxConcurrentSessions: Int = DEFAULT_GAME_SESSIONS,
    val idleTimeout: Duration = 30.seconds,
) {
    init {
        require(ioWorkerThreads > 0) { "game IO worker count must be positive" }
        require(maxConcurrentSessions > 0) { "game session limit must be positive" }
        require(idleTimeout.isPositive()) { "game idle timeout must be positive" }
    }
}
