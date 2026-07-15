package emu.game.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonActionRegistryTest {
    @Test fun `one component handler receives operation slot and object context`() {
        val handled = mutableListOf<ButtonClick>()
        val actions = buttonActions {
            onButton(interfaceId = 149, componentId = 0) { handled += it }
        }
        val click = ButtonClick(149, 0, sub = 4, obj = 995, op = 5)

        assertTrue(actions.dispatch(click))

        assertEquals(listOf(click), handled)
    }

    @Test fun `unregistered components are rejected and duplicate registrations fail`() {
        val actions = buttonActions { onButton(182, 8) {} }

        assertFalse(actions.dispatch(ButtonClick(182, 9, -1, -1, 1)))
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            buttonActions {
                onButton(182, 8) {}
                onButton(182, 8) {}
            }
        }
    }
}
