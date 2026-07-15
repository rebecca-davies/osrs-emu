package emu.persistence.character

import emu.persistence.account.PlayerRank

/** Character state loaded for one live world session. */
data class PlayerRecord(
    val id: Long,
    val displayName: String,
    val position: PlayerPosition,
    val playTimeSeconds: Long,
    val rank: PlayerRank = PlayerRank.PLAYER,
    val varps: Map<Int, Int> = emptyMap(),
    val chatFilters: PlayerChatFiltersRecord = PlayerChatFiltersRecord()
) {
    init {
        require(id > 0) { "player id must be positive" }
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
        "PlayerRecord(id=$id, position=$position, playTimeSeconds=$playTimeSeconds, rank=$rank, varps=${varps.size})"

    private companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 12
        val DISPLAY_NAME_CHARSET = charset("windows-1252")
    }
}
