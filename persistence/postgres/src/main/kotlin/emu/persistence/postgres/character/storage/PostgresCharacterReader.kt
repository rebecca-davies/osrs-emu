package emu.persistence.postgres.character.storage

import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.persistence.character.model.CharacterChatFilters
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.postgres.database.PostgresDatabase
import java.sql.Connection
import java.sql.ResultSet

private const val FIND_CHARACTER_SQL =
    "SELECT id, display_name, x, y, plane, play_time_seconds, " +
        "public_chat_mode, private_chat_mode, trade_chat_mode, gender, hair_kit, jaw_kit, " +
        "torso_kit, arms_kit, hands_kit, legs_kit, feet_kit, hair_color, torso_color, " +
        "legs_color, feet_color, skin_color " +
        "FROM players WHERE id = ?"
private const val FIND_VARPS_SQL =
    "SELECT varp, value FROM player_varps WHERE player_id = ? ORDER BY varp"

/** Reads a character aggregate from normalized PostgreSQL rows. */
internal class PostgresCharacterReader(private val database: PostgresDatabase) {
    fun load(characterId: Long): CharacterRecord? =
        database.connection { connection ->
            connection.prepareStatement(FIND_CHARACTER_SQL).use { statement ->
                statement.setLong(1, characterId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toCharacterRecord(loadVarps(connection, characterId)) else null
                }
            }
        }

    private fun loadVarps(connection: Connection, characterId: Long): Map<Int, Int> =
        connection.prepareStatement(FIND_VARPS_SQL).use { statement ->
            statement.setLong(1, characterId)
            statement.executeQuery().use { result ->
                buildMap {
                    while (result.next()) put(result.getInt("varp"), result.getInt("value"))
                }
            }
        }
}

private fun ResultSet.toCharacterRecord(varps: Map<Int, Int>): CharacterRecord =
    CharacterRecord(
        id = getLong("id"),
        displayName = getString("display_name"),
        position = CharacterPosition(getInt("x"), getInt("y"), getInt("plane")),
        playTimeSeconds = getLong("play_time_seconds"),
        varps = varps,
        chatFilters =
            CharacterChatFilters(
                publicMode = getInt("public_chat_mode"),
                privateMode = getInt("private_chat_mode"),
                tradeMode = getInt("trade_chat_mode"),
            ),
        appearance = toCharacterAppearance(),
    )

private fun ResultSet.toCharacterAppearance(): CharacterAppearance =
    CharacterAppearance(
        gender = CharacterGender.fromId(getInt("gender")),
        bodyKits =
            CharacterBodyKits(
                hair = getInt("hair_kit"),
                jaw = getInt("jaw_kit"),
                torso = getInt("torso_kit"),
                arms = getInt("arms_kit"),
                hands = getInt("hands_kit"),
                legs = getInt("legs_kit"),
                feet = getInt("feet_kit"),
            ),
        colors =
            CharacterColors(
                hair = getInt("hair_color"),
                torso = getInt("torso_color"),
                legs = getInt("legs_color"),
                feet = getInt("feet_color"),
                skin = getInt("skin_color"),
            ),
    )
