package emu.game.content.areas.inferno

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InfernoFreeModeConfigParserTest {
    @Test
    fun `editor NPC roster preserves configured order`() {
        val config = InfernoFreeModeConfigParser.parse(configuredNpcs(1 to "Jal-Nib", 2 to "Jal-Xil"))

        assertEquals(
            listOf(InfernoEditorNpc(1, "Jal-Nib"), InfernoEditorNpc(2, "Jal-Xil")),
            config.editorRoster.entries,
        )
    }

    @Test
    fun `editor NPC roster rejects duplicate types names and excess cards`() {
        assertFailsWith<IllegalArgumentException> {
            InfernoFreeModeConfigParser.parse(configuredNpcs(1 to "Jal-Nib", 1 to "Jal-Xil"))
        }
        assertFailsWith<IllegalArgumentException> {
            InfernoFreeModeConfigParser.parse(configuredNpcs(1 to "Jal-Nib", 2 to "jal-nib"))
        }
        assertFailsWith<IllegalArgumentException> {
            InfernoFreeModeConfigParser.parse(
                configuredNpcs(*(1..9).map { it to "NPC $it" }.toTypedArray()),
            )
        }
    }

    private fun configuredNpcs(vararg npcs: Pair<Int, String>): String =
        buildString {
            appendLine(
                """
                [clan_wars]
                challenge_portal_type = 1
                arrival = [5, 5, 0]

                [inferno]
                exit_portal_type = 2
                arrival = [10, 10, 0]
                south_west = [9, 9, 0]
                north_east = [20, 20, 0]
                max_npcs = 64
                """.trimIndent(),
            )
            for ((type, name) in npcs) {
                appendLine("[[inferno.npcs]]")
                appendLine("type = $type")
                appendLine("name = \"$name\"")
            }
        }
}
