package emu.persistence.account

/** Persists account privilege changes made through authorized administration. */
fun interface AccountRankStore {
    fun setRank(accountId: Long, rank: AccountRank)
}
