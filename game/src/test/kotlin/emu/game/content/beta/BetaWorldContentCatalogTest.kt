package emu.game.content.beta

import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.ui.config.UiContentCatalog
import emu.game.loc.Loc
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.npc.NpcType
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.stat.Skill
import emu.game.player.testPlayer
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.input.CountDialogInput
import emu.game.script.input.ObjDialogInput
import emu.game.script.input.TileInput
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.game.ui.Component
import emu.game.ui.PlayerInterfaceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BetaWorldContentCatalogTest {
    private val ui = UiContentCatalog.load()
    private val components = ui.components
    private val inferno =
        InfernoArena(GameMap(OpenCollisionMap), NpcCatalog.EMPTY, NpcList(), InfernoFreeModeCatalog.load())
    private val scripts = BetaWorldContentCatalog.load(ui, inferno)
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
    fun `Inferno exit portal returns the player to the hub and restores the quest tab`() {
        val config = InfernoFreeModeCatalog.load()
        val player = testPlayer(config.clanWarsArrival)
        player.activate(ui.gameframe)
        val sideDestination = components.require("toplevel_osrs_stretch:side2")
        val questJournalDestination = Component.of(629, 43)
        val challenge =
            Loc(
                type = config.challengePortalType,
                tile = config.clanWarsArrival,
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        val exit =
            Loc(
                type = config.exitPortalType,
                tile = Tile(2_269, 5_325),
                shape = 10,
                angle = 0,
                width = 5,
                length = 4,
                options = setOf(1),
            )

        assertTrue(
            runner.trigger(
                player,
                ServerTriggerType.OPLOC1,
                subject = challenge.type,
                argument = challenge,
            ),
        )
        assertEquals(370, player.interfaces.subInterfaceAt(sideDestination))
        player.interfaces.drainClientUpdates()
        player.takeGameMessages()

        assertTrue(
            runner.trigger(
                player,
                ServerTriggerType.OPLOC1,
                subject = exit.type,
                argument = exit,
            ),
        )

        assertEquals(config.clanWarsArrival, player.movement.position)
        assertEquals(MapInstance.SHARED, player.mapInstance)
        assertEquals(629, player.interfaces.subInterfaceAt(sideDestination))
        assertEquals(259, player.interfaces.subInterfaceAt(questJournalDestination))
        val interfaceUpdates = player.interfaces.drainClientUpdates()
        assertTrue(
            interfaceUpdates.contains(
                PlayerInterfaceUpdate.OpenSubInterface(sideDestination, 629, modal = false),
            ),
        )
        assertTrue(
            interfaceUpdates.contains(
                PlayerInterfaceUpdate.OpenSubInterface(questJournalDestination, 259, modal = false),
            ),
        )
        assertEquals(listOf("You leave the Inferno."), player.takeGameMessages())
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
    fun `Inferno editor places pauses resumes and clears private NPCs`() {
        val config = InfernoFreeModeCatalog.load()
        val editorNpc = requireNotNull(config.editorRoster[0])
        val npcType = NpcType(editorNpc.type, editorNpc.displayName)
        val npcs = NpcList()
        val arena =
            InfernoArena(
                GameMap(OpenCollisionMap),
                NpcCatalog { type -> npcType.takeIf { it.id == type } },
                npcs,
                config,
            )
        val editorScripts = BetaWorldContentCatalog.load(ui, arena)
        val editor = PlayerScriptRunner(editorScripts)
        val player = testPlayer(Tile(3_200, 3_200, 0))
        player.activate(ui.gameframe)
        val portal =
            Loc(
                type = config.challengePortalType,
                tile = config.clanWarsArrival,
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        assertTrue(
            editor.trigger(
                player,
                ServerTriggerType.OPLOC1,
                subject = config.challengePortalType,
                argument = portal,
            ),
        )
        val portalUpdates = player.interfaces.drainClientUpdates()
        assertTrue(
            portalUpdates.any {
                it ==
                    PlayerInterfaceUpdate.OpenSubInterface(
                        components.require("toplevel_osrs_stretch:side2"),
                        370,
                        modal = false,
                    )
            },
        )
        assertEquals(
            listOf(
                "Inferno free mode started empty and paused.",
                "Use the Inferno Editor quest tab to configure the arena.",
            ),
            player.takeGameMessages(),
        )

        val launcher = components.require("poh_options:default_building_mode_on")
        assertTrue(
            editor.start(
                player,
                requireNotNull(editorScripts.findSpecific(ServerTriggerType.IF_BUTTON, launcher.packed)),
                ButtonClick(launcher.interfaceId, launcher.componentId),
            ),
        )
        val controls = player.interfaces.drainClientUpdates()
        assertTrue(
            controls.any {
                it ==
                    PlayerInterfaceUpdate.OpenSubInterface(
                        components.require("toplevel_osrs_stretch:mainmodal"),
                        597,
                        modal = true,
                    )
            },
        )
        assertTrue(
            controls.contains(
                PlayerInterfaceUpdate.SetText(
                    components.require("bookofscrolls:title_nardah"),
                    "Jal-Nib",
                ),
            ),
        )
        assertTrue(
            controls.contains(
                PlayerInterfaceUpdate.SetText(
                    components.require("bookofscrolls:text_nardah"),
                    "Selected",
                ),
            ),
        )

        val place = components.require("bookofscrolls:teleportscroll_mosles")
        val placeScript = requireNotNull(editorScripts.findSpecific(ServerTriggerType.IF_BUTTON, place.packed))
        val pause = components.require("bookofscrolls:teleportscroll_lumberyard")
        val pauseScript = requireNotNull(editorScripts.findSpecific(ServerTriggerType.IF_BUTTON, pause.packed))
        val clear = components.require("bookofscrolls:teleportscroll_zulandra")
        val clearScript =
            requireNotNull(editorScripts.findSpecific(ServerTriggerType.IF_BUTTON, clear.packed))

        assertTrue(
            editor.start(
                player,
                pauseScript,
                ButtonClick(pause.interfaceId, pause.componentId),
            ),
        )
        assertEquals(listOf("Inferno simulation resumed."), player.takeGameMessages())
        player.interfaces.drainClientUpdates()
        assertTrue(
            editor.start(
                player,
                clearScript,
                ButtonClick(clear.interfaceId, clear.componentId),
            ),
        )
        assertEquals(
            listOf("There are no NPCs to clear."),
            player.takeGameMessages(),
        )
        assertTrue(
            player.interfaces.drainClientUpdates().contains(
                PlayerInterfaceUpdate.SetText(
                    components.require("bookofscrolls:title_lumberyard"),
                    "Resume",
                ),
            ),
        )

        assertTrue(
            editor.start(
                player,
                placeScript,
                ButtonClick(place.interfaceId, place.componentId),
            ),
        )
        assertEquals(
            listOf("Click a tile to place Jal-Nib. Press Escape to cancel."),
            player.takeGameMessages(),
        )
        assertTrue(
            player.interfaces.drainClientUpdates().any {
                it is PlayerInterfaceUpdate.CloseSubInterface
            },
        )
        assertTrue(editor.resumeInput(player, TileInput(Tile(2_273, 5_332))))
        assertEquals(listOf("Placed Jal-Nib; the simulation is paused."), player.takeGameMessages())
        assertEquals(1, npcs.count(player.mapInstance))

        assertTrue(
            editor.start(
                player,
                pauseScript,
                ButtonClick(pause.interfaceId, pause.componentId),
            ),
        )
        assertEquals(listOf("Inferno simulation resumed."), player.takeGameMessages())

        assertTrue(
            editor.start(
                player,
                clearScript,
                ButtonClick(clear.interfaceId, clear.componentId),
            ),
        )
        assertEquals(listOf("Cleared 1 NPC."), player.takeGameMessages())
        assertEquals(0, npcs.count(player.mapInstance))
    }

    @Test
    fun `logout content releases the private Inferno simulation`() {
        val config = InfernoFreeModeCatalog.load()
        val arena = InfernoArena(GameMap(OpenCollisionMap), NpcCatalog.EMPTY, NpcList(), config)
        val logoutRunner = PlayerScriptRunner(BetaWorldContentCatalog.load(ui, arena))
        val player = testPlayer(config.clanWarsArrival)
        arena.enter(player)

        assertTrue(logoutRunner.trigger(player, ServerTriggerType.LOGOUT))

        assertFalse(arena.isActive(player))
    }
}
