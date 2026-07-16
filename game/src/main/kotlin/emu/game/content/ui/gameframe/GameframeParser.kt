package emu.game.content.ui.gameframe

import emu.game.ui.Component
import org.tomlj.Toml
import org.tomlj.TomlTable

/** Parses the initial gameframe tree from UI content TOML. */
object GameframeParser {
    fun parse(source: String): Gameframe {
        val result = Toml.parse(source)
        require(!result.hasErrors()) {
            result.errors().joinToString(prefix = "invalid UI content TOML: ")
        }
        val table = requireNotNull(result.getTable(GAMEFRAME_TABLE)) {
            "UI content TOML is missing [$GAMEFRAME_TABLE]"
        }
        return from(table)
    }

    internal fun from(table: TomlTable): Gameframe {
        val topLevel = table.requireUnsignedShort(TOP_LEVEL)
        val array = requireNotNull(table.getArray(SUB_INTERFACES)) {
            "UI content TOML is missing $GAMEFRAME_TABLE.$SUB_INTERFACES"
        }
        val subInterfaces =
            List(array.size()) { index ->
                val sub = requireNotNull(array.getTable(index)) {
                    "$GAMEFRAME_TABLE.$SUB_INTERFACES[$index] must be a table"
                }
                GameframeSubInterface(
                    destination = Component(sub.requireUnsignedInt(DESTINATION).toInt()),
                    interfaceId = sub.requireUnsignedShort(INTERFACE),
                    modal = sub.getBoolean(MODAL) ?: false,
                )
            }
        val inventories =
            table.getArray(INITIAL_INVENTORIES)?.let { inventories ->
                List(inventories.size()) { index ->
                    val inventory = requireNotNull(inventories.getTable(index)) {
                        "$INITIAL_INVENTORIES[$index] must be a table"
                    }
                    GameframeInventory(
                        componentId = inventory.requireUnsignedShort(COMPONENT),
                        inventoryId = inventory.requireUnsignedShort(INVENTORY),
                    )
                }
            }.orEmpty()
        return Gameframe(topLevel, subInterfaces, inventories)
    }

    private fun TomlTable.requireUnsignedShort(name: String): Int {
        val value = getLong(name)
        require(value != null && value in 0..0xFFFF) { "$name must fit an unsigned short" }
        return value.toInt()
    }

    private fun TomlTable.requireUnsignedInt(name: String): Long {
        val value = getLong(name)
        require(value != null && value in 0..0xFFFF_FFFFL) { "$name must fit an unsigned int" }
        return value
    }

    private const val GAMEFRAME_TABLE = "gameframe"
    private const val TOP_LEVEL = "top_level"
    private const val SUB_INTERFACES = "sub_interfaces"
    private const val DESTINATION = "destination"
    private const val INTERFACE = "interface"
    private const val MODAL = "modal"
    private const val INITIAL_INVENTORIES = "initial_inventories"
    private const val COMPONENT = "component"
    private const val INVENTORY = "inventory"
}
