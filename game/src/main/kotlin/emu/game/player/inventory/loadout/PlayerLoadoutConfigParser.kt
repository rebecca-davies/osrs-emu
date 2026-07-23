package emu.game.player.inventory.loadout

import emu.game.obj.Wearpos
import emu.game.player.inventory.PlayerInventory
import java.util.Locale
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/** Parses bounded, human-readable player loadout configuration. */
internal object PlayerLoadoutConfigParser {
    fun parse(source: String): List<PlayerLoadoutConfig> {
        require(source.length <= MAX_SOURCE_LENGTH) {
            "loadout TOML exceeds $MAX_SOURCE_LENGTH characters"
        }
        val result = Toml.parse(source)
        require(!result.hasErrors()) {
            result.errors().joinToString(prefix = "invalid loadout TOML: ")
        }
        val array = result.getArray(LOADOUTS) ?: return emptyList()
        require(array.size() <= MAX_LOADOUTS) { "at most $MAX_LOADOUTS loadouts may be configured" }
        val loadouts = array.readLoadouts()
        require(loadouts.map { it.name.lowercase(Locale.ROOT) }.distinct().size == loadouts.size) {
            "loadout names must be unique"
        }
        return loadouts
    }

    private fun TomlArray.readLoadouts(): List<PlayerLoadoutConfig> =
        (0 until size().toInt()).map { index ->
            val table = requireNotNull(getTable(index)) { "loadout $index must be a table" }
            val name = table.requireLoadoutName()
            PlayerLoadoutConfig(
                name = name,
                worn = table.requireArray(WORN).readObjects("loadout '$name' worn", Wearpos.entries.size),
                inventory =
                    table.requireArray(INVENTORY).readObjects(
                        "loadout '$name' inventory",
                        PlayerInventory.CAPACITY,
                    ),
            )
        }

    private fun TomlArray.readObjects(owner: String, maximum: Int): List<ObjStackConfig> {
        require(size() <= maximum) { "$owner has more than $maximum entries" }
        return (0 until size().toInt()).map { index ->
            val table = requireNotNull(getTable(index)) { "$owner entry $index must be a table" }
            ObjStackConfig(
                name = table.requireObjectName(),
                type = table.optionalInt(TYPE, UNSIGNED_SHORT),
                count = table.optionalInt(COUNT, 1..Int.MAX_VALUE) ?: 1,
            )
        }
    }

    private fun TomlTable.requireArray(name: String): TomlArray =
        requireNotNull(getArray(name)) { "$name must be an array" }

    private fun TomlTable.requireLoadoutName(): String =
        requireText(NAME).also {
            require(it.length <= PlayerLoadout.MAX_NAME_LENGTH) {
                "loadout name exceeds ${PlayerLoadout.MAX_NAME_LENGTH} characters"
            }
            require(SEPARATOR !in it) { "loadout name cannot contain '$SEPARATOR'" }
        }

    private fun TomlTable.requireObjectName(): String =
        requireText(NAME).also {
            require(it.length <= MAX_OBJECT_NAME_LENGTH) {
                "object name exceeds $MAX_OBJECT_NAME_LENGTH characters"
            }
        }

    private fun TomlTable.requireText(name: String): String {
        val value = getString(name)
        require(!value.isNullOrBlank()) { "$name must be non-blank" }
        require('\u0000' !in value) { "$name cannot contain a NUL character" }
        require(CP_1252.newEncoder().canEncode(value)) { "$name must be encodable as CP-1252" }
        return value
    }

    private fun TomlTable.optionalInt(name: String, range: IntRange): Int? {
        val value = getLong(name) ?: return null
        require(value in range.first.toLong()..range.last.toLong()) { "$name must be in $range" }
        return value.toInt()
    }

    private const val LOADOUTS = "loadouts"
    private const val NAME = "name"
    private const val TYPE = "type"
    private const val COUNT = "count"
    private const val WORN = "worn"
    private const val INVENTORY = "inventory"
    private const val SEPARATOR = '|'
    private const val MAX_SOURCE_LENGTH = 131_072
    private const val MAX_LOADOUTS = 32
    private const val MAX_OBJECT_NAME_LENGTH = 80
    private val UNSIGNED_SHORT = 0..0xFFFF
    private val CP_1252 = charset("windows-1252")
}
