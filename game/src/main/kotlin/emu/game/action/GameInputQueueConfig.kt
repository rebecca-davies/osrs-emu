package emu.game.action

/** Validated memory and per-cycle limits for one connection's action queue. */
data class GameInputQueueConfig(
    val capacity: Int = 128,
    val maxPerCycle: Int = 32,
) {
    init {
        require(capacity > 0) { "game input queue capacity must be positive" }
        require(maxPerCycle in 1..capacity) {
            "game input cycle budget must be between 1 and capacity"
        }
    }
}
