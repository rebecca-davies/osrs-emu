package emu.game.player

import emu.game.content.player.PlayerVarpCatalog
import emu.game.pathfinding.Tile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTest {
    @Test
    fun `saved run mode hydrates movement state`() {
        val running =
            Player(
                Tile(3200, 3200),
                savedVarps = mapOf(PlayerVarpCatalog.RUN_MODE.id to 1),
            )
        val walking = Player(Tile(3200, 3200))

        assertTrue(running.movement.runEnabled)
        assertFalse(walking.movement.runEnabled)
    }
}
