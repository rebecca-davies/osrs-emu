package emu.persistence.account

/** Persistence operations required by account authentication and administration. */
interface AccountStore {
    fun findByUsername(username: String): StoredAccount?

    fun create(
        username: String,
        displayName: String,
        passwordHash: String,
    ): StoredAccount?

    fun setRank(accountId: Long, rank: PlayerRank)
}
