package emu.game.action

/** Thread-safe bounded capability exposed to one player's network handlers. */
fun interface PlayerActionSink {
    fun submit(action: PlayerAction): Boolean
}
