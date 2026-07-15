package emu.server.world.config

import emu.server.world.runtime.PlayerCapacity

private const val DEFAULT_GAME_IO_WORKERS = 8

/** Independent limits for game connection IO and authoritative world execution. */
data class GameExecutionConfig(
    val ioWorkerThreads: Int = DEFAULT_GAME_IO_WORKERS,
    val maxConcurrentSessions: Int = PlayerCapacity.PER_WORLD,
    val connection: GameConnectionConfig = GameConnectionConfig(),
) {
    init {
        require(ioWorkerThreads > 0) { "game IO worker count must be positive" }
        require(maxConcurrentSessions in 1..PlayerCapacity.PER_WORLD) {
            "game session limit must be between 1 and ${PlayerCapacity.PER_WORLD}"
        }
    }
}
