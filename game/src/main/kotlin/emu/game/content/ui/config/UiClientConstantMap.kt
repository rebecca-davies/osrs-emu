package emu.game.content.ui.config

import org.tomlj.TomlTable

/** Immutable Jagex-name-to-integer client constants parsed from content TOML. */
class UiClientConstantMap private constructor(private val constants: Map<String, Int>) {
    fun require(name: String): Int =
        requireNotNull(constants[name]) { "UI client constant mapping is missing: $name" }

    companion object {
        val EMPTY = UiClientConstantMap(emptyMap())

        internal fun from(table: TomlTable?): UiClientConstantMap {
            if (table == null) return EMPTY
            val constants =
                table.keySet().associateWith { name ->
                    val value = table.get(listOf(name)) as? Long
                    require(value != null && value in Int.MIN_VALUE..Int.MAX_VALUE) {
                        "UI client constant '$name' must fit a signed integer"
                    }
                    value.toInt()
                }
            return UiClientConstantMap(constants)
        }
    }
}
