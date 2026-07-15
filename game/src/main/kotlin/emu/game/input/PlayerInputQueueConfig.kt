package emu.game.input

/** Validated memory and per-cycle limits for one player's input queue. */
data class PlayerInputQueueConfig(
    val capacity: Int = 128,
    val maxPerCycle: Int = 32,
) {
    init {
        require(capacity > 0) { "player input queue capacity must be positive" }
        require(maxPerCycle > 0) { "player input cycle budget must be positive" }
    }
}
