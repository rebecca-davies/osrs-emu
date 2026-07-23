package emu.game.content.areas.inferno

import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.GameMap
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.testPlayer
import emu.game.ui.Component
import emu.game.ui.PlayerInterfaceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InfernoEditorInterfaceTest {
    @Test
    fun `editor owns and explicitly restores the quest tab destination`() {
        val ui = UiContentCatalog.load()
        val config = InfernoFreeModeCatalog.load()
        val arena = InfernoArena(GameMap(OpenCollisionMap), NpcCatalog.EMPTY, NpcList(), config)
        val editor = InfernoEditorInterface(ui.components, config.editorRoster)
        val player = testPlayer(config.clanWarsArrival)
        val sideDestination = ui.components.require("toplevel_osrs_stretch:side2")
        val questJournalDestination = Component.of(629, 43)
        assertTrue(
            ui.gameframe.subInterfaces.any {
                it.destination == questJournalDestination && it.interfaceId == 259
            },
        )
        player.activate(ui.gameframe)
        val state = arena.enter(player)

        editor.openLauncher(player, state)
        assertEquals(370, player.interfaces.subInterfaceAt(sideDestination))
        val launcherUpdates = player.interfaces.drainClientUpdates()
        for (componentId in 14..19) {
            assertTrue(
                launcherUpdates.contains(
                    PlayerInterfaceUpdate.SetHidden(
                        Component.of(370, componentId),
                        hidden = true,
                    ),
                ),
            )
        }
        editor.openControls(player, state)
        assertTrue(player.interfaces.hasModal())

        player.interfaces.drainClientUpdates()
        editor.close(player)

        assertFalse(player.interfaces.hasModal())
        assertEquals(629, player.interfaces.subInterfaceAt(sideDestination))
        assertEquals(259, player.interfaces.subInterfaceAt(questJournalDestination))
        val updates = player.interfaces.drainClientUpdates()
        assertTrue(updates.any { it is PlayerInterfaceUpdate.CloseSubInterface })
        assertTrue(
            updates.any {
                it ==
                    PlayerInterfaceUpdate.OpenSubInterface(
                        sideDestination,
                        629,
                        modal = false,
                    )
            },
        )
        assertTrue(
            updates.any {
                it ==
                    PlayerInterfaceUpdate.OpenSubInterface(
                        questJournalDestination,
                        259,
                        modal = false,
                    )
            },
        )
    }
}
