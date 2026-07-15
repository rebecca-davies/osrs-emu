package emu.server.world.player

import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonClick
import emu.server.world.session.initialPlayerVarps
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerGameStateTest {
    @Test fun `both captured run buttons change movement and persistent varp on the game thread`() {
        for (component in listOf(116 to 30, 160 to 28)) {
            val varps = initialPlayerVarps().apply { markClientSynchronized() }
            val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
            val control = PlayerSessionControl()
            val actions = playerButtonActions(movement, varps, control)

            assertTrue(runBlocking { actions.dispatch(ButtonClick(component.first, component.second, -1, -1, 1)) })

            assertTrue(movement.runEnabled)
            assertEquals(1, varps[PlayerVarpTypes.RUN_MODE])
            assertEquals(mapOf(173 to 1), varps.dirtyPersistentValues())
            assertEquals(1, varps.drainClientUpdates().single().value)
        }
    }

    @Test fun `logout button requests clean session exit while other ops do nothing`() {
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val control = PlayerSessionControl()
        val actions = playerButtonActions(movement, varps, control)

        runBlocking { actions.dispatch(ButtonClick(182, 8, -1, -1, 2)) }
        assertFalse(control.logoutRequested)

        runBlocking { actions.dispatch(ButtonClick(182, 8, -1, -1, 1)) }
        assertTrue(control.logoutRequested)
    }
}
