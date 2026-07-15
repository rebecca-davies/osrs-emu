package emu.server.login.auth

private const val MIN_NAME_LENGTH = 1
private const val MAX_NAME_LENGTH = 12
private val NAME_SEPARATORS = setOf(' ', '_', '-')
private val SEPARATOR_RUN = Regex("[ _-]+")

/** Canonical account name plus the case-preserving display name submitted at first login. */
data class PlayerIdentity(
    val username: String,
    val displayName: String,
) {
    companion object {
        fun parse(input: String): PlayerIdentity? {
            val displayName = input.trim()
            if (displayName.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) return null
            if (!displayName.first().isAsciiLetterOrDigit() || !displayName.last().isAsciiLetterOrDigit()) return null
            if (!displayName.all { it.isAsciiLetterOrDigit() || it in NAME_SEPARATORS }) return null
            val username = displayName.lowercase().replace(SEPARATOR_RUN, " ")
            return PlayerIdentity(username, displayName)
        }
    }
}

private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
