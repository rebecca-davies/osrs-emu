package emu.game.queue

/** Interruption and scheduling priorities supported by a player action queue. */
enum class PlayerActionPriority {
    NORMAL,
    LONG,
    ENGINE,
    WEAK,
    STRONG,
}
