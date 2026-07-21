package emu.persistence.postgres.account

import emu.game.player.appearance.CharacterAppearance
import emu.persistence.account.AccountRecord
import emu.persistence.account.AccountStore
import emu.persistence.account.StoredAccount
import emu.persistence.postgres.character.storage.bindCharacterAppearance
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.ResultSet

private const val FIND_ACCOUNT_SQL =
    "SELECT id, username, password_hash, display_name, rank FROM players WHERE username = ?"
private const val CREATE_ACCOUNT_SQL =
    "INSERT INTO players(username, password_hash, display_name) " +
        "VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING " +
        "RETURNING id, username, password_hash, display_name, rank"
private const val INITIALIZE_CHARACTER_APPEARANCE_SQL =
    "UPDATE players SET gender = ?, hair_kit = ?, jaw_kit = ?, torso_kit = ?, arms_kit = ?, " +
        "hands_kit = ?, legs_kit = ?, feet_kit = ?, hair_color = ?, torso_color = ?, " +
        "legs_color = ?, feet_color = ?, skin_color = ? WHERE id = ?"

/** PostgreSQL account adapter that atomically creates credentials and initial character state. */
class PostgresAccountStore(
    private val database: PostgresDatabase,
    private val newCharacterAppearance: () -> CharacterAppearance,
) : AccountStore {
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
        database.transaction { connection ->
            val created =
                connection.prepareStatement(CREATE_ACCOUNT_SQL).use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, passwordHash)
                    statement.setString(3, displayName)
                    statement.executeQuery().use { result -> if (result.next()) result.toStoredAccount() else null }
                } ?: return@transaction null
            val appearance = newCharacterAppearance()
            connection.prepareStatement(INITIALIZE_CHARACTER_APPEARANCE_SQL).use { statement ->
                statement.bindCharacterAppearance(1, appearance)
                statement.setLong(14, created.account.id)
                check(statement.executeUpdate() == 1) {
                    "new character ${created.account.id} disappeared during initialization"
                }
            }
            created
        }
}

private fun ResultSet.toStoredAccount(): StoredAccount =
    StoredAccount(
        account =
            AccountRecord(
                id = getLong("id"),
                username = getString("username"),
                displayName = getString("display_name"),
                rank = PostgresAccountRankMapper.fromId(getInt("rank")),
            ),
        passwordHash = getString("password_hash"),
    )
