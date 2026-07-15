package emu.persistence

/** Case-insensitive bcrypt authentication with atomic first-login account creation. */
class AccountService(
    private val players: PlayerRepository,
    private val passwords: PasswordHasher,
) {
    fun loginOrCreate(
        submittedUsername: String,
        password: CharArray,
        spawn: PlayerPosition,
    ): AuthenticationResult {
        val identity = PlayerIdentity.parse(submittedUsername) ?: return AuthenticationResult.InvalidCredentials
        val existing = players.findByUsername(identity.username)
        if (existing != null) return authenticate(existing, password)

        val hash = passwords.hash(password)
        val created = players.createAccount(identity, hash, spawn)
        if (created != null) return AuthenticationResult.Authenticated(created.player, created = true)

        val concurrent = players.findByUsername(identity.username)
            ?: error("account creation conflicted but the player row was not visible")
        return authenticate(concurrent, password)
    }

    private fun authenticate(
        stored: PlayerRepository.StoredPlayer,
        password: CharArray,
    ): AuthenticationResult =
        if (passwords.verify(password, stored.passwordHash)) {
            AuthenticationResult.Authenticated(stored.player, created = false)
        } else {
            AuthenticationResult.InvalidCredentials
        }
}
