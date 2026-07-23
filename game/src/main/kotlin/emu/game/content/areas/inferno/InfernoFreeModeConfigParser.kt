package emu.game.content.areas.inferno

import emu.game.map.Tile
import emu.game.npc.NpcList
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/** Parses authoritative Inferno free-mode configuration from TOML. */
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
            exitPortalType = inferno.requireInt(EXIT_PORTAL_TYPE, 0..0xFFFF),
            clanWarsArrival = clanWars.requireTile(ARRIVAL),
            arenaArrival = inferno.requireTile(ARRIVAL),
            arenaBounds =
                InfernoArenaBounds(
                    inferno.requireTile(SOUTH_WEST),
                    inferno.requireTile(NORTH_EAST),
                ),
            maxNpcs = inferno.requireInt(MAX_NPCS, 1..NpcList.DEFAULT_CAPACITY),
            editorRoster = inferno.requireEditorRoster(),
        )
    }

    private fun TomlTable.requireEditorRoster(): InfernoEditorRoster {
        val roster = requireNotNull(getArray(NPCS)) { "$INFERNO.$NPCS must be an array of tables" }
        return InfernoEditorRoster(
            List(roster.size()) { index ->
                val npc = requireNotNull(roster.getTable(index)) {
                    "$INFERNO.$NPCS[$index] must be a table"
                }
                InfernoEditorNpc(
                    type = npc.requireInt(TYPE, NPC_TYPES),
                    displayName = npc.requireString(NAME),
                )
            },
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

    private fun TomlTable.requireString(name: String): String {
        val value = getString(name)
        require(!value.isNullOrBlank()) { "$name must be a non-blank string" }
        return value
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
    private const val EXIT_PORTAL_TYPE = "exit_portal_type"
    private const val ARRIVAL = "arrival"
    private const val SOUTH_WEST = "south_west"
    private const val NORTH_EAST = "north_east"
    private const val MAX_NPCS = "max_npcs"
    private const val NPCS = "npcs"
    private const val TYPE = "type"
    private const val NAME = "name"
    private const val TILE_COMPONENTS = 3
    private val WORLD_COORDINATES = 0..0x3FFF
    private val PLANES = 0..3
    private val NPC_TYPES = 0 until (1 shl 14)
}
