package emu.game.cheat

/** Bounded developer-console text staged for world-thread command selection. */
data class PlayerCheatInput(val text: String) {
    init {
        require(text.length <= MAX_LENGTH && '\u0000' !in text) { "invalid player cheat input" }
    }

    private companion object {
        const val MAX_LENGTH = 80
    }
}
