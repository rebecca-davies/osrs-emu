package emu.game.npc

import emu.game.varp.PlayerVariable
import emu.game.varp.PlayerVariableValues
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpcOperationsTest {
    private val variable = PlayerVariable.Varp(1)

    @Test
    fun `first matching conditional operation replaces the plain operation`() {
        val operations =
            NpcOperations(
                options = setOf(2),
                conditionalOptions =
                    mapOf(
                        2 to
                            listOf(
                                NpcConditionalOperation(variable, 1..2, visible = false),
                                NpcConditionalOperation(variable, 2..3, visible = true),
                            ),
                    ),
            )

        assertTrue(operations.supports(2, 0, values(0)))
        assertFalse(operations.supports(2, 0, values(2)))
        assertTrue(operations.supports(2, 0, values(3)))
    }

    @Test
    fun `conditional nested operation resolves independently at its exact sub option`() {
        val operations =
            NpcOperations(
                conditionalSubOptions =
                    mapOf(
                        2 to
                            mapOf(
                                7 to listOf(NpcConditionalOperation(variable, 4..4, visible = true)),
                            ),
                    ),
            )

        assertFalse(operations.supports(2, 7, values(3)))
        assertTrue(operations.supports(2, 7, values(4)))
        assertFalse(operations.supports(2, 8, values(4)))
    }

    private fun values(value: Int): PlayerVariableValues =
        PlayerVariableValues { selected -> if (selected == variable) value else 0 }
}
