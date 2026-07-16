package emu.server.game.network.output.ui

import emu.game.content.ui.config.UiContentCatalog
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.message.inventory.UpdateInvFull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayerInterfaceOutputTest {
    private val output = PlayerInterfaceOutput(UiContentCatalog.load().gameframe)

    @Test
    fun `configured frame contains permanent overlays without a welcome modal`() {
        val subs = output.frameSubInterfaces()

        assertEquals(25, subs.size)
        assertEquals(IfOpenSub(161, 96, 162), subs.first())
        assertTrue(subs.none { it.type == IfOpenSub.MODAL || it.interfaceId == 378 })
        assertTrue(subs.contains(IfOpenSub(707, 7, 7)))
    }

    @Test
    fun `frame and initial inventories come from the same UI model`() {
        val messages = output.frameMessages()

        assertEquals(IfOpenTop(161), messages.first())
        assertIs<IfOpenSub>(messages[1])
        assertEquals(
            listOf(UpdateInvFull(-1, 64209, 93), UpdateInvFull(-1, 64208, 94)),
            output.initialInventories(),
        )
    }
}
