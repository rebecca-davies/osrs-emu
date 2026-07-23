package emu.game.content.beta.supplies

import org.tomlj.Toml
import org.tomlj.TomlTable

/** Parses bounded Tournament Supplies policy without coupling content to cache storage. */
internal object TournamentSuppliesConfigParser {
    fun parse(source: String): TournamentSuppliesConfig {
        require(source.length <= MAX_SOURCE_LENGTH) {
            "Tournament Supplies TOML exceeds $MAX_SOURCE_LENGTH characters"
        }
        val result = Toml.parse(source)
        require(!result.hasErrors()) {
            result.errors().joinToString(prefix = "invalid Tournament Supplies TOML: ")
        }
        val supplies = requireNotNull(result.getTable(SUPPLIES)) {
            "Tournament Supplies TOML is missing [$SUPPLIES]"
        }
        return TournamentSuppliesConfig(
            previewInventory = supplies.requireInt(PREVIEW_INVENTORY, UNSIGNED_SHORT),
            itemCatalogueEnum = supplies.requireInt(ITEM_CATALOGUE_ENUM, UNSIGNED_SHORT),
            itemStackSize = supplies.requireInt(ITEM_STACK_SIZE, 1..Int.MAX_VALUE),
        )
    }

    private fun TomlTable.requireInt(name: String, range: IntRange): Int {
        val value = getLong(name)
        require(value != null && value in range.first.toLong()..range.last.toLong()) {
            "$name must be in $range"
        }
        return value.toInt()
    }

    private const val SUPPLIES = "supplies"
    private const val PREVIEW_INVENTORY = "preview_inventory"
    private const val ITEM_CATALOGUE_ENUM = "item_catalogue_enum"
    private const val ITEM_STACK_SIZE = "item_stack_size"
    private const val MAX_SOURCE_LENGTH = 16_384
    private val UNSIGNED_SHORT = 0..0xFFFF
}
