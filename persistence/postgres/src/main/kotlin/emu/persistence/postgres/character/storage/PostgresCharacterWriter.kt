package emu.persistence.postgres.character.storage

import emu.persistence.character.model.CharacterSave
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.Connection

private const val UPDATE_CHARACTER_SQL =
    "UPDATE players SET x = ?, y = ?, plane = ?, " +
        "play_time_seconds = GREATEST(play_time_seconds, ?), public_chat_mode = ?, " +
        "private_chat_mode = ?, trade_chat_mode = ? WHERE id = ?"
private const val DELETE_VARP_SQL = "DELETE FROM player_varps WHERE player_id = ? AND varp = ?"
private const val UPSERT_VARP_SQL =
    "INSERT INTO player_varps(player_id, varp, value) VALUES (?, ?, ?) " +
        "ON CONFLICT (player_id, varp) DO UPDATE SET value = EXCLUDED.value"

/** Atomically writes one character save point and its sparse dirty varps. */
internal class PostgresCharacterWriter(private val database: PostgresDatabase) {
    fun save(save: CharacterSave) {
        database.transaction { connection ->
            updateCharacter(connection, save)
            deleteClearedVarps(connection, save)
            upsertChangedVarps(connection, save)
        }
    }

    private fun updateCharacter(connection: Connection, save: CharacterSave) {
        connection.prepareStatement(UPDATE_CHARACTER_SQL).use { statement ->
            statement.setInt(1, save.position.x)
            statement.setInt(2, save.position.y)
            statement.setInt(3, save.position.plane)
            statement.setLong(4, save.playTimeSeconds)
            statement.setInt(5, save.chatFilters.publicMode)
            statement.setInt(6, save.chatFilters.privateMode)
            statement.setInt(7, save.chatFilters.tradeMode)
            statement.setLong(8, save.characterId)
            check(statement.executeUpdate() == 1) { "character ${save.characterId} no longer exists" }
        }
    }

    private fun deleteClearedVarps(connection: Connection, save: CharacterSave) {
        val cleared = save.dirtyVarps.filterValues { it == 0 }.keys
        if (cleared.isEmpty()) return
        connection.prepareStatement(DELETE_VARP_SQL).use { statement ->
            for (varp in cleared) {
                statement.setLong(1, save.characterId)
                statement.setInt(2, varp)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun upsertChangedVarps(connection: Connection, save: CharacterSave) {
        val changed = save.dirtyVarps.filterValues { it != 0 }
        if (changed.isEmpty()) return
        connection.prepareStatement(UPSERT_VARP_SQL).use { statement ->
            for ((varp, value) in changed) {
                statement.setLong(1, save.characterId)
                statement.setInt(2, varp)
                statement.setInt(3, value)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
