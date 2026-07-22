package emu.game.content.ui.config

import emu.game.ui.ClientScript
import org.tomlj.TomlTable

/** Immutable Jagex-name-to-client-script mappings parsed from content TOML. */
class UiClientScriptMap private constructor(private val scripts: Map<String, ClientScript>) {
    fun require(name: String): ClientScript =
        requireNotNull(scripts[name]) { "UI client-script mapping is missing: $name" }

    companion object {
        val EMPTY = UiClientScriptMap(emptyMap())

        internal fun from(table: TomlTable?): UiClientScriptMap {
            if (table == null) return EMPTY
            val scripts =
                table.keySet().associateWith { name ->
                    val id = table.get(listOf(name)) as? Long
                    require(id != null && id in 0..Int.MAX_VALUE) {
                        "UI client script '$name' must be a non-negative integer"
                    }
                    ClientScript(id.toInt())
                }
            return UiClientScriptMap(scripts)
        }
    }
}
