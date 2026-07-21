package emu.persistence.character.model

import emu.game.player.appearance.CharacterAppearance

/** Character state loaded for one live world session. */
data class CharacterRecord(
    val id: Long,
    val displayName: String,
    val position: CharacterPosition,
    val playTimeSeconds: Long,
    val varps: Map<Int, Int> = emptyMap(),
    val chatFilters: CharacterChatFilters = CharacterChatFilters(),
    val appearance: CharacterAppearance = CharacterAppearance.DEFAULT,
) {
    init {
        require(id > 0) { "character id must be positive" }
        require(
            displayName.isNotBlank() &&
                displayName.length <= MAX_DISPLAY_NAME_LENGTH &&
                '\u0000' !in displayName,
        ) {
            "display name must contain 1..$MAX_DISPLAY_NAME_LENGTH non-NUL characters"
        }
        require(DISPLAY_NAME_CHARSET.newEncoder().canEncode(displayName)) {
            "display name must be encodable as CP-1252"
        }
        require(playTimeSeconds >= 0) { "play time cannot be negative" }
    }

    override fun toString(): String =
        "CharacterRecord(id=$id, position=$position, playTimeSeconds=$playTimeSeconds, varps=${varps.size})"

    private companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 12
        val DISPLAY_NAME_CHARSET = charset("windows-1252")
    }
}
