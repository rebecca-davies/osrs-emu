package emu.game.player.inventory.loadout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerLoadoutConfigParserTest {
    @Test
    fun `parser accepts the configured loadout and backpack entry limits`() {
        val inventory = List(28) { "{ name = \"Item $it\", type = ${it + 1} }" }.joinToString()
        val source =
            List(32) { index ->
                loadout(name = "Loadout $index", inventory = inventory)
            }.joinToString("\n")

        val config = PlayerLoadoutConfigParser.parse(source)

        assertEquals(32, config.size)
        assertEquals(28, config.first().inventory.size)
    }

    @Test
    fun `parser rejects a thirty-third loadout`() {
        val source = List(33) { loadout("Loadout $it") }.joinToString("\n")

        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(source)
        }
    }

    @Test
    fun `parser rejects a twenty-ninth backpack entry`() {
        val inventory = List(29) { "{ name = \"Item $it\", type = ${it + 1} }" }.joinToString()

        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(loadout("Too many", inventory))
        }
    }

    @Test
    fun `parser bounds worn entries and object values`() {
        val worn = List(15) { "{ name = \"Item $it\", type = ${it + 1} }" }.joinToString()
        listOf(
            loadout(name = "Too much worn", worn = worn),
            loadout(name = "Bad type", inventory = "{ name = \"Item\", type = 65536 }"),
            loadout(name = "Bad count", inventory = "{ name = \"Item\", type = 1, count = 0 }"),
        ).forEach { source ->
            assertFailsWith<IllegalArgumentException> {
                PlayerLoadoutConfigParser.parse(source)
            }
        }
    }

    @Test
    fun `parser bounds loadout and object names`() {
        val acceptedName = "L".repeat(40)
        val acceptedObjectName = "O".repeat(80)
        val accepted =
            PlayerLoadoutConfigParser.parse(
                loadout(acceptedName, "{ name = \"$acceptedObjectName\", type = 1 }"),
            )
        assertEquals(acceptedName, accepted.single().name)

        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(loadout("L".repeat(41)))
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(
                loadout("Objects", "{ name = \"${"O".repeat(81)}\", type = 1 }"),
            )
        }
    }

    @Test
    fun `parser rejects duplicate names and oversized source before retaining it`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(loadout("Ranged") + "\n" + loadout("rAnGeD"))
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfigParser.parse(" ".repeat(131_073))
        }
    }

    private fun loadout(
        name: String,
        inventory: String = "",
        worn: String = "",
    ): String =
        """
        [[loadouts]]
        name = "$name"
        worn = [$worn]
        inventory = [$inventory]
        """.trimIndent()
}
