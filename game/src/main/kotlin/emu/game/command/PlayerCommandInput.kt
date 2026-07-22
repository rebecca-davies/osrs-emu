package emu.game.command

/** Bounded developer-console text staged for world-thread command selection. */
data class PlayerCommandInput(val text: String) {
    init {
        require(text.length <= MAX_LENGTH && '\u0000' !in text) { "invalid player command input" }
    }

    private companion object {
        const val MAX_LENGTH = 80
    }
}
