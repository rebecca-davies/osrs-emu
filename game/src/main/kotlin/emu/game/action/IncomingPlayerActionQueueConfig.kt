package emu.game.action

/** Validated memory and per-cycle limits for one connection's action queue. */
data class IncomingPlayerActionQueueConfig(
    val capacity: Int = 128,
    val maxPerCycle: Int = 32,
) {
    init {
        require(capacity > 0) { "incoming player action queue capacity must be positive" }
        require(maxPerCycle in 1..capacity) {
            "incoming player action cycle budget must be between 1 and capacity"
        }
    }
}
