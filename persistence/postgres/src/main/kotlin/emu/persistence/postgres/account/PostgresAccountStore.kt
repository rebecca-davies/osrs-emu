package emu.persistence.postgres.account

import emu.persistence.account.AccountRecord
import emu.persistence.account.AccountStore
import emu.persistence.account.StoredAccount
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.ResultSet

private const val FIND_ACCOUNT_SQL =
    "SELECT id, username, password_hash, display_name, rank FROM players WHERE username = ?"
private const val CREATE_ACCOUNT_SQL =
    "INSERT INTO players(username, password_hash, display_name) " +
        "VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING " +
        "RETURNING id, username, password_hash, display_name, rank"
/** PostgreSQL account credential adapter. */
class PostgresAccountStore(private val database: PostgresDatabase) : AccountStore {
    override fun findByUsername(username: String): StoredAccount? =
        database.connection { connection ->
            connection.prepareStatement(FIND_ACCOUNT_SQL).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { result -> if (result.next()) result.toStoredAccount() else null }
            }
        }

    override fun create(
        username: String,
        displayName: String,
        passwordHash: String,
    ): StoredAccount? =
        database.connection { connection ->
            connection.prepareStatement(CREATE_ACCOUNT_SQL).use { statement ->
                statement.setString(1, username)
                statement.setString(2, passwordHash)
                statement.setString(3, displayName)
                statement.executeQuery().use { result -> if (result.next()) result.toStoredAccount() else null }
            }
        }
}

private fun ResultSet.toStoredAccount(): StoredAccount =
    StoredAccount(
        account =
            AccountRecord(
                id = getLong("id"),
                username = getString("username"),
                displayName = getString("display_name"),
                rank = PostgresPlayerRankMapper.fromId(getInt("rank")),
            ),
        passwordHash = getString("password_hash"),
    )
