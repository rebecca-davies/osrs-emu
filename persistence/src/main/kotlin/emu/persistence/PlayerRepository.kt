package emu.persistence

import java.sql.ResultSet

/** Thin JDBC persistence for account rows and write-behind session saves. */
class PlayerRepository(private val database: PostgresDatabase) {
    internal fun findByUsername(username: String): StoredPlayer? =
        database.connection { connection ->
            connection.prepareStatement(
                "SELECT id, username, password_hash, display_name, x, y, plane, play_time_seconds, rank " +
                    "FROM players WHERE username = ?",
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { result -> if (result.next()) result.storedPlayer() else null }
            }
        }

    /** Inserts a first-login account, returning `null` if another login created it concurrently. */
    internal fun createAccount(
        identity: PlayerIdentity,
        passwordHash: String,
        spawn: PlayerPosition,
    ): StoredPlayer? =
        database.connection { connection ->
            connection.prepareStatement(
                "INSERT INTO players(username, password_hash, display_name, x, y, plane) " +
                    "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (username) DO NOTHING " +
                    "RETURNING id, username, password_hash, display_name, x, y, plane, play_time_seconds, rank",
            ).use { statement ->
                statement.setString(1, identity.username)
                statement.setString(2, passwordHash)
                statement.setString(3, identity.displayName)
                statement.setInt(4, spawn.x)
                statement.setInt(5, spawn.y)
                statement.setInt(6, spawn.plane)
                statement.executeQuery().use { result -> if (result.next()) result.storedPlayer() else null }
            }
        }

    /** Atomically flushes final position and adds this session's monotonic elapsed time. */
    fun saveSession(playerId: Long, position: PlayerPosition, playedSeconds: Long) {
        require(playedSeconds >= 0) { "played time cannot be negative" }
        require(position.x in WORLD_COORDINATES && position.y in WORLD_COORDINATES) {
            "position outside world: $position"
        }
        require(position.plane in PLANES) { "invalid plane ${position.plane}" }
        database.connection { connection ->
            connection.prepareStatement(
                "UPDATE players SET x = ?, y = ?, plane = ?, " +
                    "play_time_seconds = play_time_seconds + ? WHERE id = ?",
            ).use { statement ->
                statement.setInt(1, position.x)
                statement.setInt(2, position.y)
                statement.setInt(3, position.plane)
                statement.setLong(4, playedSeconds)
                statement.setLong(5, playerId)
                check(statement.executeUpdate() == 1) { "player $playerId no longer exists" }
            }
        }
    }

    /** Administrative privilege update; the new rank takes effect on the player's next login. */
    fun setRank(playerId: Long, rank: PlayerRank) {
        database.connection { connection ->
            connection.prepareStatement("UPDATE players SET rank = ? WHERE id = ?").use { statement ->
                statement.setInt(1, rank.id)
                statement.setLong(2, playerId)
                check(statement.executeUpdate() == 1) { "player $playerId no longer exists" }
            }
        }
    }

    private fun ResultSet.storedPlayer(): StoredPlayer =
        StoredPlayer(
            player =
                PlayerRecord(
                    id = getLong("id"),
                    username = getString("username"),
                    displayName = getString("display_name"),
                    position = PlayerPosition(getInt("x"), getInt("y"), getInt("plane")),
                    playTimeSeconds = getLong("play_time_seconds"),
                    rank = PlayerRank.fromId(getInt("rank")),
                ),
            passwordHash = getString("password_hash"),
        )

    internal data class StoredPlayer(val player: PlayerRecord, val passwordHash: String)

    private companion object {
        val WORLD_COORDINATES = 0..0x3FFF
        val PLANES = 0..3
    }
}
