package emu.persistence.postgres.character

import emu.persistence.character.PlayerSessionSave
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.Connection

private const val UPDATE_SESSION_SQL =
    "UPDATE players SET x = ?, y = ?, plane = ?, " +
        "play_time_seconds = play_time_seconds + ? WHERE id = ?"
private const val DELETE_VARP_SQL = "DELETE FROM player_varps WHERE player_id = ? AND varp = ?"
private const val UPSERT_VARP_SQL =
    "INSERT INTO player_varps(player_id, varp, value) VALUES (?, ?, ?) " +
        "ON CONFLICT (player_id, varp) DO UPDATE SET value = EXCLUDED.value"

/** Atomically writes one character save point and its sparse dirty varps. */
internal class PostgresSessionWriter(private val database: PostgresDatabase) {
    fun save(save: PlayerSessionSave) {
        database.transaction { connection ->
            updateSession(connection, save)
            deleteClearedVarps(connection, save)
            upsertChangedVarps(connection, save)
        }
    }

    private fun updateSession(connection: Connection, save: PlayerSessionSave) {
        connection.prepareStatement(UPDATE_SESSION_SQL).use { statement ->
            statement.setInt(1, save.position.x)
            statement.setInt(2, save.position.y)
            statement.setInt(3, save.position.plane)
            statement.setLong(4, save.playedSeconds)
            statement.setLong(5, save.playerId)
            check(statement.executeUpdate() == 1) { "player ${save.playerId} no longer exists" }
        }
    }

    private fun deleteClearedVarps(connection: Connection, save: PlayerSessionSave) {
        val cleared = save.dirtyVarps.filterValues { it == 0 }.keys
        if (cleared.isEmpty()) return
        connection.prepareStatement(DELETE_VARP_SQL).use { statement ->
            for (varp in cleared) {
                statement.setLong(1, save.playerId)
                statement.setInt(2, varp)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun upsertChangedVarps(connection: Connection, save: PlayerSessionSave) {
        val changed = save.dirtyVarps.filterValues { it != 0 }
        if (changed.isEmpty()) return
        connection.prepareStatement(UPSERT_VARP_SQL).use { statement ->
            for ((varp, value) in changed) {
                statement.setLong(1, save.playerId)
                statement.setInt(2, varp)
                statement.setInt(3, value)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
