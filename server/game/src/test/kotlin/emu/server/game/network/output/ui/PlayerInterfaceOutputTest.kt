package emu.server.game.network.output.ui

import emu.game.content.ui.config.UiContentCatalog
import emu.game.obj.Obj
import emu.game.ui.Component
import emu.game.ui.PlayerInterfaceUpdate
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenTop
import emu.protocol.osrs239.game.message.component.IfSetHide
import emu.protocol.osrs239.game.message.component.IfSetText
import emu.protocol.osrs239.game.message.inventory.UpdateInvFull
import emu.server.game.testPlayer
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
        val inventories = output.initialInventories(testPlayer())
        assertEquals(listOf(93, 94), inventories.map(UpdateInvFull::inventoryId))
        assertEquals(List(28) { UpdateInvFull.Obj(-1, 0) }, inventories[0].objects)
        assertEquals(List(14) { UpdateInvFull.Obj(-1, 0) }, inventories[1].objects)
    }

    @Test
    fun `component state updates map to revision specific messages`() {
        val component = Component.of(597, 4)

        assertEquals(
            IfSetText(597, 4, "Inferno"),
            PlayerInterfaceOutput.message(PlayerInterfaceUpdate.SetText(component, "Inferno")),
        )
        assertEquals(
            IfSetHide(597, 4, hidden = true),
            PlayerInterfaceOutput.message(PlayerInterfaceUpdate.SetHidden(component, hidden = true)),
        )
    }

    @Test
    fun `temporary preview updates preserve the exact client inventory shape`() {
        assertEquals(
            UpdateInvFull(
                interfaceId = -1,
                componentId = -1,
                inventoryId = 207,
                objects = listOf(UpdateInvFull.Obj(4_151, 1), UpdateInvFull.Obj(-1, 0)),
            ),
            PlayerInterfaceOutput.message(
                PlayerInterfaceUpdate.TransmitInventory(207, listOf(Obj(4_151), null)),
            ),
        )
    }
}
