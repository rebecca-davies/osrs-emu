package emu.game.content.player.login

/** Game content displayed after a player enters the world. */
data class LoginNotice(val text: String) {
    init {
        require(text.isNotBlank()) { "login notice text must not be blank" }
        require('\u0000' !in text) { "login notice text must not contain a string terminator" }
    }
}
