package emu.persistence

/** Case-insensitive account identity plus the case-preserving in-game name. */
data class PlayerIdentity(
    val username: String,
    val displayName: String,
) {
    companion object {
        /** Parses an OSRS-style name while retaining the player's chosen case and separators. */
        fun parse(input: String): PlayerIdentity? {
            val displayName = input.trim()
            if (displayName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) return null
            if (!displayName.first().isAsciiLetterOrDigit() || !displayName.last().isAsciiLetterOrDigit()) return null
            if (!displayName.all { it.isAsciiLetterOrDigit() || it in NAME_SEPARATORS }) return null
            val username = displayName.lowercase().replace(SEPARATOR_RUN, " ")
            return PlayerIdentity(username, displayName)
        }

        private const val MIN_NAME_LENGTH = 1
        private const val MAX_NAME_LENGTH = 12
        private val NAME_SEPARATORS = setOf(' ', '_', '-')
        private val SEPARATOR_RUN = Regex("[ _-]+")
    }
}

/** Persisted position, independent of the game module's live movement type. */
data class PlayerPosition(val x: Int, val y: Int, val plane: Int)

/** Stable persisted privilege levels, independent of any one client's wire representation. */
enum class PlayerRank(val id: Int) {
    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2),
    ;

    companion object {
        fun fromId(id: Int): PlayerRank =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("unsupported player rank id $id")
    }
}

/** Account and character state loaded into one live game session. */
data class PlayerRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val position: PlayerPosition,
    val playTimeSeconds: Long,
    val rank: PlayerRank = PlayerRank.PLAYER,
    /** Sparse permanent player variables. Defaults live in the game varp catalog. */
    val varps: Map<Int, Int> = emptyMap(),
)

/** Identity-only account data returned by authentication. */
data class AccountRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val rank: PlayerRank,
)

/** Result of case-insensitive account authentication or first-login creation. */
sealed interface AccountAuthenticationResult {
    data class Authenticated(val account: AccountRecord, val created: Boolean) : AccountAuthenticationResult

    data object InvalidCredentials : AccountAuthenticationResult
}

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
