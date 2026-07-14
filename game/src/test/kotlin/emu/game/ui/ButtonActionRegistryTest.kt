package emu.game.ui

import emu.game.cycle.GameCycle
import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ButtonActionRegistryTest {
    @Test fun `network submissions are bounded and handlers run only on the game cycle`() {
        val clicks = PlayerButtonQueue(capacity = 1)
        val handled = mutableListOf<ButtonClick>()
        val actions = buttonActions {
            onButton(interfaceId = 160, componentId = 28) { handled += it }
        }
        val cycle = GameCycle(clicks.cycleProcesses(actions))
        val click = ButtonClick(160, 28, sub = -1, obj = -1, op = 1)

        assertTrue(clicks.submit(click))
        assertFalse(clicks.submit(click), "full mailbox must apply back-pressure")
        assertTrue(handled.isEmpty(), "the network coroutine must not execute game actions")

        runSuspending { cycle.tick() }

        assertEquals(listOf(click), handled)
    }

    @Test fun `one component handler receives operation slot and object context`() {
        val handled = mutableListOf<ButtonClick>()
        val actions = buttonActions {
            onButton(interfaceId = 149, componentId = 0) { handled += it }
        }
        val click = ButtonClick(149, 0, sub = 4, obj = 995, op = 5)

        assertTrue(runSuspending { actions.dispatch(click) })

        assertEquals(listOf(click), handled)
    }

    @Test fun `unregistered components are rejected and duplicate registrations fail`() {
        val actions = buttonActions { onButton(182, 8) {} }

        assertFalse(runSuspending { actions.dispatch(ButtonClick(182, 9, -1, -1, 1)) })
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            buttonActions {
                onButton(182, 8) {}
                onButton(182, 8) {}
            }
        }
    }
}
