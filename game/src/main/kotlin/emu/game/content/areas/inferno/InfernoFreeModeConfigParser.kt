package emu.game.content.areas.inferno

import emu.game.map.Tile
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/** Parses the Inferno free-mode content coordinates and cache type from TOML. */
object InfernoFreeModeConfigParser {
    fun parse(source: String): InfernoFreeModeConfig {
        val result = Toml.parse(source)
        require(!result.hasErrors()) {
            result.errors().joinToString(prefix = "invalid Inferno free-mode TOML: ")
        }
        val clanWars = result.requireTable(CLAN_WARS)
        val inferno = result.requireTable(INFERNO)
        return InfernoFreeModeConfig(
            challengePortalType = clanWars.requireInt(CHALLENGE_PORTAL_TYPE, 0..0xFFFF),
            clanWarsArrival = clanWars.requireTile(ARRIVAL),
            arenaArrival = inferno.requireTile(ARRIVAL),
        )
    }

    private fun TomlTable.requireTable(name: String): TomlTable =
        requireNotNull(getTable(name)) { "Inferno free-mode TOML is missing [$name]" }

    private fun TomlTable.requireTile(name: String): Tile {
        val coordinates = requireNotNull(getArray(name)) { "$name must be a coordinate array" }
        require(coordinates.size() == TILE_COMPONENTS) { "$name must contain x, z, and level" }
        return Tile(
            coordinates.requireInt(0, WORLD_COORDINATES),
            coordinates.requireInt(1, WORLD_COORDINATES),
            coordinates.requireInt(2, PLANES),
        )
    }

    private fun TomlTable.requireInt(name: String, range: IntRange): Int {
        val value = getLong(name)
        require(value != null && value in range.first.toLong()..range.last.toLong()) {
            "$name must be in $range"
        }
        return value.toInt()
    }

    private fun TomlArray.requireInt(index: Int, range: IntRange): Int {
        val value = getLong(index)
        require(value in range.first.toLong()..range.last.toLong()) {
            "coordinate $index must be in $range"
        }
        return value.toInt()
    }

    private const val CLAN_WARS = "clan_wars"
    private const val INFERNO = "inferno"
    private const val CHALLENGE_PORTAL_TYPE = "challenge_portal_type"
    private const val ARRIVAL = "arrival"
    private const val TILE_COMPONENTS = 3
    private val WORLD_COORDINATES = 0..0x3FFF
    private val PLANES = 0..3
}
