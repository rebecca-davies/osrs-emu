package emu.server.session

/** Opaque key joining login reservation and world handoff. */
@JvmInline
value class GameSessionToken(val value: String) {
    init {
        require(value.isNotBlank()) { "game session token must not be blank" }
    }
}
