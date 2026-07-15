package emu.persistence.postgres.character

import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.postgres.account.PostgresPlayerRankMapper
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.Connection
import java.sql.ResultSet

private const val FIND_CHARACTER_SQL =
    "SELECT id, username, display_name, x, y, plane, play_time_seconds, rank " +
        "FROM players WHERE id = ?"
private const val FIND_VARPS_SQL =
    "SELECT varp, value FROM player_varps WHERE player_id = ? ORDER BY varp"

/** Reads a character aggregate from normalized PostgreSQL rows. */
internal class PostgresCharacterReader(private val database: PostgresDatabase) {
    fun load(playerId: Long): PlayerRecord? =
        database.connection { connection ->
            connection.prepareStatement(FIND_CHARACTER_SQL).use { statement ->
                statement.setLong(1, playerId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toPlayerRecord(loadVarps(connection, playerId)) else null
                }
            }
        }

    private fun loadVarps(connection: Connection, playerId: Long): Map<Int, Int> =
        connection.prepareStatement(FIND_VARPS_SQL).use { statement ->
            statement.setLong(1, playerId)
            statement.executeQuery().use { result ->
                buildMap {
                    while (result.next()) put(result.getInt("varp"), result.getInt("value"))
                }
            }
        }
}

private fun ResultSet.toPlayerRecord(varps: Map<Int, Int>): PlayerRecord =
    PlayerRecord(
        id = getLong("id"),
        username = getString("username"),
        displayName = getString("display_name"),
        position = PlayerPosition(getInt("x"), getInt("y"), getInt("plane")),
        playTimeSeconds = getLong("play_time_seconds"),
        rank = PostgresPlayerRankMapper.fromId(getInt("rank")),
        varps = varps,
    )
