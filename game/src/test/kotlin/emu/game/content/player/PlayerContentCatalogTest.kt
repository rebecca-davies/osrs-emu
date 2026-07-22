package emu.game.content.player

import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.obj.ObjCatalog
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import emu.game.player.stat.Skill
import emu.game.player.testPlayer
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.input.CountDialogInput
import emu.game.script.input.ObjDialogInput
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.game.ui.PlayerInterfaceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerContentCatalogTest {
    private val ui = UiContentCatalog.load()
    private val components = ui.components
    private val scripts = PlayerContentCatalog.load(ui)
    private val runner = PlayerScriptRunner(scripts)

    @Test
    fun `login starts every character in the shared Clan Wars hub`() {
        val player = testPlayer(Tile(2_271, 5_332, 0))
        player.teleportTo(player.movement.position, MapInstance.privateTo(player.id))

        assertTrue(runner.trigger(player, ServerTriggerType.LOGIN))

        assertEquals(InfernoFreeModeCatalog.load().clanWarsArrival, player.movement.position)
        assertEquals(MapInstance.SHARED, player.mapInstance)
    }

    @Test
    fun `run buttons share feature-local toggle content`() {
        val player = testPlayer(Tile(3200, 3200, 0))
        val orb = components.require("orbs:runbutton")
        val settings = components.require("settings_side:runmode")

        runner.start(
            player,
            requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, orb.packed)),
            ButtonClick(orb.interfaceId, orb.componentId),
        )
        assertTrue(player.movement.runEnabled)

        runner.start(
            player,
            requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, settings.packed)),
            ButtonClick(settings.interfaceId, settings.componentId),
        )
        assertFalse(player.movement.runEnabled)
    }

    @Test
    fun `logout button requests the normal player lifecycle`() {
        val player = testPlayer(Tile(3200, 3200, 0))
        val component = components.require("logout:logout")
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed))

        runner.start(player, script, ButtonClick(component.interfaceId, component.componentId))

        assertTrue(player.logoutRequested)
    }

    @Test
    fun `skill button edits authoritative level through the Jagex count dialog`() {
        val player = testPlayer(Tile(3200, 3200, 0))
        player.activate(ui.gameframe)
        val component = components.require("stats:attack")
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed))

        assertTrue(runner.start(player, script, ButtonClick(component.interfaceId, component.componentId)))
        val update = player.interfaces.drainClientUpdates().single() as PlayerInterfaceUpdate.RunClientScript
        assertEquals(108, update.script.id)
        assertTrue(player.isAccessProtected)

        assertFalse(runner.resumeInput(player, ObjDialogInput(4_151)))
        assertTrue(player.isAccessProtected)
        assertTrue(runner.resumeInput(player, CountDialogInput(70)))
        assertEquals(70, player.stats[Skill.ATTACK].baseLevel)
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `skill content rejects non-primary and inventory-shaped button operations`() {
        val player = testPlayer(Tile(3200, 3200, 0))
        player.activate(ui.gameframe)
        val component = components.require("stats:attack")
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed))

        runner.start(
            player,
            script,
            ButtonClick(component.interfaceId, component.componentId, sub = 0, obj = 0, op = 2),
        )

        assertTrue(player.interfaces.drainClientUpdates().isEmpty())
        assertEquals(99, player.stats[Skill.ATTACK].baseLevel)
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `beta equipment picker validates the selected cache object before equipping it`() {
        val whip = ObjType(4_151, "Abyssal whip", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val catalog = ObjCatalog { type -> whip.takeIf { it.id == type } }
        val pickerScripts = PlayerContentCatalog.load(ui, catalog)
        val picker = PlayerScriptRunner(pickerScripts)
        val component = components.require("wornitems:equipment")
        val script =
            requireNotNull(
                pickerScripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed),
            )
        val player = testPlayer(Tile(3_200, 3_200, 0))
        player.activate(ui.gameframe)

        assertTrue(picker.start(player, script, ButtonClick(component.interfaceId, component.componentId)))
        val open = player.interfaces.drainClientUpdates().single() as PlayerInterfaceUpdate.RunClientScript
        assertEquals(750, open.script.id)
        assertEquals(listOf("Choose an item:", 0, -1, 1), open.arguments)

        assertTrue(picker.resumeInput(player, ObjDialogInput(whip.id)))
        assertEquals(whip.id, player.worn[Wearpos.RIGHT_HAND]?.obj?.type)
        assertEquals(listOf("Equipped Abyssal whip."), player.takeGameMessages())
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `beta equipment picker rejects an unknown cache object`() {
        val pickerScripts = PlayerContentCatalog.load(ui, ObjCatalog.EMPTY)
        val picker = PlayerScriptRunner(pickerScripts)
        val component = components.require("wornitems:equipment")
        val script = requireNotNull(pickerScripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed))
        val player = testPlayer(Tile(3_200, 3_200, 0))
        player.activate(ui.gameframe)

        assertTrue(picker.start(player, script, ButtonClick(component.interfaceId, component.componentId)))
        player.interfaces.drainClientUpdates()
        assertTrue(picker.resumeInput(player, ObjDialogInput(65_535)))

        assertEquals(listOf("That item is not available in this beta world."), player.takeGameMessages())
        assertTrue(player.worn.loginSync().all { it == null })
        assertTrue(player.inventory.loginSync().all { it == null })
        assertFalse(player.isAccessProtected)
    }
}
