package emu.server.bot.account

import java.security.SecureRandom

/** Creates dummy credentials whose successful localhost logins follow normal account persistence. */
internal class BotAccountGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    fun next(): BotCredentials {
        val username = buildString(USERNAME_LENGTH) {
            append(USERNAME_PREFIX)
            repeat(USERNAME_LENGTH - USERNAME_PREFIX.length) {
                append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.size)])
            }
        }
        val password = CharArray(PASSWORD_LENGTH) { ALPHANUMERIC[random.nextInt(ALPHANUMERIC.size)] }
        return BotCredentials(username, password)
    }

    private companion object {
        const val USERNAME_PREFIX = "Bot"
        const val USERNAME_LENGTH = 12
        const val PASSWORD_LENGTH = 24
        val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray()
    }
}

/** Mutable password ownership is cleared immediately after the login packet is built. */
internal class BotCredentials(
    val username: String,
    val password: CharArray,
) {
    fun clear() = password.fill('\u0000')

    override fun toString(): String = "BotCredentials(username=<redacted>, password=<redacted>)"
}
