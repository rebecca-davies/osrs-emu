package emu.game.input

/** Thread-safe bounded input capability exposed to one player's network handlers. */
fun interface PlayerInputSink {
    fun submit(input: PlayerInput): Boolean
}
