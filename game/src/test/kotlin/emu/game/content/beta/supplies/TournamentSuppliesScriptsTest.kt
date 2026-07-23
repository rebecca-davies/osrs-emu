package emu.game.content.beta.supplies

import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.obj.NamedObjEnumCatalog
import emu.game.obj.ObjCatalog
import emu.game.obj.ObjStack
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import emu.game.player.inventory.loadout.PlayerLoadout
import emu.game.player.testPlayer
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.game.ui.PlayerInterfaceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TournamentSuppliesScriptsTest {
    private val ui = UiContentCatalog.load()
    private val components = ui.components

    @Test
    fun `stock modal opens in cache order and applies one contributed loadout`() {
        val whip = ObjType(4_151, "Abyssal whip", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val staff = ObjType(1_389, "Magic staff", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val catalog = ObjCatalog { type -> whip.takeIf { it.id == type } }
        val loadout = requireNotNull(PlayerLoadout.create("Inferno", listOf(ObjStack(whip)), emptyList()))
        val scripts = scripts(supplies(catalog, listOf(whip.id), listOf(loadout)))
        val runner = PlayerScriptRunner(scripts)
        val equipment = components.require("wornitems:equipment")
        val player = testPlayer(Tile(3_200, 3_200, 0))
        player.activate(ui.gameframe)
        player.interfaces.drainClientUpdates()

        assertTrue(
            runner.start(
                player,
                requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, equipment.packed)),
                ButtonClick(equipment.interfaceId, equipment.componentId),
            ),
        )
        val openUpdates = player.interfaces.drainClientUpdates()
        val prepareModal = assertIs<PlayerInterfaceUpdate.RunClientScript>(openUpdates[0])
        assertEquals(2524, prepareModal.script.id)
        assertEquals(listOf(-1, -1), prepareModal.arguments)
        val openModal = assertIs<PlayerInterfaceUpdate.OpenSubInterface>(openUpdates[1])
        assertEquals(100, openModal.interfaceId)
        assertTrue(openModal.modal)
        assertEquals(2687, assertIs<PlayerInterfaceUpdate.RunClientScript>(openUpdates[2]).script.id)
        val switchLayer = assertIs<PlayerInterfaceUpdate.RunClientScript>(openUpdates[3])
        assertEquals(2697, switchLayer.script.id)
        assertEquals(
            listOf(
                components.require("tournament_supplies:loadout_container").packed,
                components.require("tournament_supplies:switchlayer").packed,
                components.require("tournament_supplies:item_container").packed,
                "Items",
                components.require("tournament_supplies:loadout_container").packed,
                "Load-outs",
                components.require("tournament_supplies:list").packed,
                components.require("tournament_supplies:scrollbar").packed,
                components.require("tournament_supplies:search").packed,
                9,
                42,
            ),
            switchLayer.arguments,
        )
        assertIs<PlayerInterfaceUpdate.TransmitInventory>(openUpdates[4])

        val catalogue = components.require("tournament_supplies:list")
        assertTrue(
            runner.start(
                player,
                requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, catalogue.packed)),
                ButtonClick(catalogue.interfaceId, catalogue.componentId, sub = 0, obj = whip.id),
            ),
        )
        assertEquals(whip.id, player.worn[Wearpos.RIGHT_HAND]?.obj?.type)
        assertEquals(listOf("Equipped Abyssal whip."), player.takeGameMessages())

        player.worn.equip(staff)
        val apply = components.require("tournament_supplies:loadout_apply")
        assertTrue(
            runner.start(
                player,
                requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, apply.packed)),
                ButtonClick(apply.interfaceId, apply.componentId, sub = 0),
            ),
        )
        assertEquals(staff.id, player.worn[Wearpos.RIGHT_HAND]?.obj?.type)
        assertTrue(player.takeGameMessages().isEmpty())

        assertTrue(
            runner.start(
                player,
                requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, apply.packed)),
                ButtonClick(apply.interfaceId, apply.componentId),
            ),
        )

        assertEquals(whip.id, player.worn[Wearpos.RIGHT_HAND]?.obj?.type)
        assertEquals(listOf("Applied Inferno."), player.takeGameMessages())
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `cache-valid objects outside the Jagex catalogue enum are rejected`() {
        val permitted = ObjType(4_151, "Abyssal whip", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val forged = ObjType(4_152, "Cache-valid forgery", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val objects = mapOf(permitted.id to permitted, forged.id to forged)
        val scripts = scripts(supplies(ObjCatalog(objects::get), listOf(permitted.id)))
        val runner = PlayerScriptRunner(scripts)
        val equipment = components.require("wornitems:equipment")
        val player = testPlayer(Tile(3_200, 3_200, 0))
        player.activate(ui.gameframe)

        runner.start(
            player,
            requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, equipment.packed)),
            ButtonClick(equipment.interfaceId, equipment.componentId),
        )
        player.interfaces.drainClientUpdates()
        val catalogue = components.require("tournament_supplies:list")
        assertTrue(
            runner.start(
                player,
                requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, catalogue.packed)),
                ButtonClick(catalogue.interfaceId, catalogue.componentId, sub = 0, obj = forged.id),
            ),
        )

        assertEquals(listOf("That item is not available in this beta world."), player.takeGameMessages())
        assertTrue(player.worn.loginSync().all { it == null })
        assertTrue(player.inventory.loginSync().all { it == null })
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `contributed loadouts cannot expand the beta item catalogue`() {
        val permitted = ObjType(4_151, "Abyssal whip", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val unavailable = ObjType(4_152, "Unavailable staff", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val objects = mapOf(permitted.id to permitted, unavailable.id to unavailable)
        val loadout =
            requireNotNull(PlayerLoadout.create("Inferno", listOf(ObjStack(unavailable)), emptyList()))

        assertFailsWith<IllegalArgumentException> {
            supplies(ObjCatalog(objects::get), listOf(permitted.id), listOf(loadout))
        }
    }

    @Test
    fun `catalogue and contributed loadout counts are bounded before use`() {
        assertFailsWith<IllegalArgumentException> {
            supplies(ObjCatalog.EMPTY, List(1_025) { it })
        }
        val empty = requireNotNull(PlayerLoadout.create("Empty", emptyList(), emptyList()))
        assertFailsWith<IllegalArgumentException> {
            supplies(ObjCatalog.EMPTY, emptyList(), List(33) { empty })
        }
    }

    private fun scripts(supplies: TournamentSupplies): PlayerScriptRepository =
        PlayerScriptRepository.build(ui) { TournamentSuppliesScripts.register(this, supplies) }

    private fun supplies(
        objs: ObjCatalog,
        catalogueIds: List<Int>,
        loadouts: List<PlayerLoadout> = emptyList(),
    ): TournamentSupplies =
        TournamentSupplies(
            TournamentSuppliesConfig(
                previewInventory = 207,
                itemCatalogueEnum = 1,
                itemStackSize = 10_000,
            ),
            ui,
            objs,
            NamedObjEnumCatalog { enumId -> catalogueIds.takeIf { enumId == 1 } },
            loadouts,
        )
}
