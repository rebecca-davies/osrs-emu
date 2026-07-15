package emu.game.content.ui

import emu.game.ui.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UiComponentMapTest {
    @Test
    fun `TOML names resolve packed interface components`() {
        val components =
            UiComponentMap.parse(
                """
                [components]
                "orbs:runbutton" = 10485788
                "logout:logout" = 11927560
                """.trimIndent(),
            )

        assertEquals(Component.of(160, 28), components.require("orbs:runbutton"))
        assertEquals(Component.of(182, 8), components.require("logout:logout"))
    }

    @Test
    fun `malformed TOML and non-numeric component ids are rejected`() {
        assertFailsWith<IllegalArgumentException> { UiComponentMap.parse("[components") }
        assertFailsWith<IllegalArgumentException> {
            UiComponentMap.parse("[components]\n\"bad\" = \"not an id\"")
        }
    }

    @Test
    fun `bundled catalog owns the bootstrap component data`() {
        val components = UiContentCatalog.load().components

        assertEquals(Component.of(116, 30), components.require("settings_side:runmode"))
    }
}
