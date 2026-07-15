package emu.persistence.account

/** Persistence operations required by account authentication. */
interface AccountStore {
    fun findByUsername(username: String): StoredAccount?

    fun create(
        username: String,
        displayName: String,
        passwordHash: String,
    ): StoredAccount?
}
