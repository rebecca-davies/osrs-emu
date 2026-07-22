package emu.server.game.config

import emu.server.game.runtime.command.WorldCommandQueueConfig
import emu.server.game.world.entry.PlayerCapacity

private const val DEFAULT_CONNECTION_WORKERS = 8
private const val DEFAULT_ENTRY_WORKERS = 4

/** Independent limits for game connection IO and authoritative world execution. */
data class GameExecutionConfig(
    val connectionWorkerThreads: Int = DEFAULT_CONNECTION_WORKERS,
    val entryWorkerThreads: Int = DEFAULT_ENTRY_WORKERS,
    val maxConcurrentSessions: Int = PlayerCapacity.PER_WORLD,
    val connection: GameConnectionConfig = GameConnectionConfig(),
    val commands: WorldCommandQueueConfig = WorldCommandQueueConfig(),
    val collisionLoads: CollisionLoadQueueConfig = CollisionLoadQueueConfig(),
) {
    init {
        require(connectionWorkerThreads > 0) { "game connection worker count must be positive" }
        require(entryWorkerThreads > 0) { "world entry worker count must be positive" }
        require(maxConcurrentSessions in 1..PlayerCapacity.PER_WORLD) {
            "game session limit must be between 1 and ${PlayerCapacity.PER_WORLD}"
        }
    }
}
