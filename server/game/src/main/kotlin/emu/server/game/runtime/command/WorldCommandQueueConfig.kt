package emu.server.game.runtime.command

/** Memory and per-cycle limits for commands crossing into the world thread. */
data class WorldCommandQueueConfig(
    val capacity: Int = 1_024,
    val maxPerCycle: Int = 256,
) {
    init {
        require(capacity > 0) { "world command queue capacity must be positive" }
        require(maxPerCycle in 1..capacity) {
            "world command cycle budget must be between 1 and capacity"
        }
    }
}
