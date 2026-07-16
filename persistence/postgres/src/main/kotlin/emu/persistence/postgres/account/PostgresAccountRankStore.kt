package emu.persistence.postgres.account

import emu.persistence.account.AccountRank
import emu.persistence.account.AccountRankStore
import emu.persistence.postgres.database.PostgresDatabase

private const val UPDATE_RANK_SQL = "UPDATE players SET rank = ? WHERE id = ?"

/** PostgreSQL account privilege adapter. */
class PostgresAccountRankStore(private val database: PostgresDatabase) : AccountRankStore {
    override fun setRank(accountId: Long, rank: AccountRank) {
        database.connection { connection ->
            connection.prepareStatement(UPDATE_RANK_SQL).use { statement ->
                statement.setInt(1, rank.id)
                statement.setLong(2, accountId)
                check(statement.executeUpdate() == 1) { "account $accountId no longer exists" }
            }
        }
    }
}
