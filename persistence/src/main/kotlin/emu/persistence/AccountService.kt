package emu.persistence

/** Case-insensitive bcrypt authentication with atomic first-login account creation. */
class AccountService(
    private val players: PlayerRepository,
    private val passwords: PasswordHasher,
) {
    fun loginOrCreate(
        submittedUsername: String,
        password: CharArray,
    ): AccountAuthenticationResult {
        val identity = PlayerIdentity.parse(submittedUsername) ?: return AccountAuthenticationResult.InvalidCredentials
        val existing = players.findAccountByUsername(identity.username)
        if (existing != null) return authenticate(existing, password)

        val hash = passwords.hash(password)
        val created = players.createAccount(identity, hash)
        if (created != null) return AccountAuthenticationResult.Authenticated(created.account, created = true)

        val concurrent = players.findAccountByUsername(identity.username)
            ?: error("account creation conflicted but the player row was not visible")
        return authenticate(concurrent, password)
    }

    private fun authenticate(
        stored: PlayerRepository.StoredAccount,
        password: CharArray,
    ): AccountAuthenticationResult =
        if (passwords.verify(password, stored.passwordHash)) {
            AccountAuthenticationResult.Authenticated(stored.account, created = false)
        } else {
            AccountAuthenticationResult.InvalidCredentials
        }
}
