package emu.game.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerInterfacesTest {
    @Test
    fun `component updates require visible synchronized components and preserve order`() {
        val interfaces = PlayerInterfaces()
        interfaces.openTopLevel(161)
        interfaces.openOverlay(Component.of(161, 78), 370)
        interfaces.markClientSynchronized()
        val component = Component.of(370, 4)

        interfaces.setText(component, "Paused")
        interfaces.setHidden(component, hidden = false)

        assertEquals(
            listOf(
                PlayerInterfaceUpdate.SetText(component, "Paused"),
                PlayerInterfaceUpdate.SetHidden(component, hidden = false),
            ),
            interfaces.drainClientUpdates(),
        )
        assertFailsWith<IllegalArgumentException> {
            interfaces.setText(Component.of(597, 4), "not visible")
        }
    }

    @Test
    fun `component text remains explicitly bounded`() {
        val interfaces = PlayerInterfaces()
        interfaces.openTopLevel(161)
        interfaces.markClientSynchronized()
        val component = Component.of(161, 1)

        assertFailsWith<IllegalArgumentException> {
            interfaces.setText(component, "x".repeat(1_025))
        }
        assertFailsWith<IllegalArgumentException> {
            interfaces.setText(component, "not cp1252: 😀")
        }
        assertFailsWith<IllegalArgumentException> {
            interfaces.setText(component, "before\u0000after")
        }
    }

    @Test
    fun `replaced overlays restore their complete retained subtree`() {
        val interfaces = PlayerInterfaces()
        val side = Component.of(161, 78)
        val questJournal = Component.of(629, 43)
        val layout =
            InterfaceLayout(
                destination = side,
                interfaceId = 370,
                visibleComponents = listOf(Component.of(370, 0)),
                hiddenComponents = listOf(Component.of(370, 14)),
            )
        interfaces.openTopLevel(161)
        interfaces.openOverlay(side, 629)
        interfaces.openOverlay(questJournal, 259)
        interfaces.markClientSynchronized()

        assertTrue(interfaces.replaceOverlay(layout))
        assertEquals(370, interfaces.subInterfaceAt(side))
        assertEquals(null, interfaces.subInterfaceAt(questJournal))
        assertFalse(interfaces.isVisible(Component.of(259, 0)))
        assertEquals(
            listOf(
                PlayerInterfaceUpdate.OpenSubInterface(side, 370, modal = false),
                PlayerInterfaceUpdate.SetHidden(Component.of(370, 0), hidden = false),
                PlayerInterfaceUpdate.SetHidden(Component.of(370, 14), hidden = true),
            ),
            interfaces.drainClientUpdates(),
        )

        assertTrue(interfaces.restoreOverlay(layout))
        assertEquals(629, interfaces.subInterfaceAt(side))
        assertEquals(259, interfaces.subInterfaceAt(questJournal))
        assertTrue(interfaces.isVisible(Component.of(259, 0)))
        assertEquals(
            listOf(
                PlayerInterfaceUpdate.OpenSubInterface(side, 629, modal = false),
                PlayerInterfaceUpdate.OpenSubInterface(questJournal, 259, modal = false),
            ),
            interfaces.drainClientUpdates(),
        )
    }
}
