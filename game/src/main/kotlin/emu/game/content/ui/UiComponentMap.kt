package emu.game.content.ui

import emu.game.ui.Component
import org.tomlj.Toml
import org.tomlj.TomlTable

/** Immutable Jagex-name-to-component mappings parsed from content TOML. */
class UiComponentMap private constructor(private val components: Map<String, Component>) {
    fun require(name: String): Component =
        requireNotNull(components[name]) { "UI component mapping is missing: $name" }

    companion object {
        fun parse(source: String): UiComponentMap {
            val result = Toml.parse(source)
            require(!result.hasErrors()) {
                result.errors().joinToString(prefix = "invalid UI component TOML: ")
            }
            val table = requireNotNull(result.getTable(COMPONENT_TABLE)) {
                "UI component TOML is missing [$COMPONENT_TABLE]"
            }
            return from(table)
        }

        internal fun from(table: TomlTable): UiComponentMap {
            val components =
                table.keySet().associateWith { name ->
                    val packed = table.get(listOf(name)) as? Long
                    require(packed != null && packed in 0..UNSIGNED_INT_MAX) {
                        "UI component '$name' must be an unsigned packed component"
                    }
                    Component(packed.toInt())
                }
            return UiComponentMap(components)
        }

        private const val COMPONENT_TABLE = "components"
        private const val UNSIGNED_INT_MAX = 0xFFFF_FFFFL
    }
}
