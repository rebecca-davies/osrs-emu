package emu.game.player

import emu.game.content.player.PlayerVarpCatalog
import emu.game.map.Tile
import emu.game.queue.PlayerActionPriority
import emu.game.script.execution.PlayerScript
import emu.game.script.execution.PlayerScriptRequest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `closing a modal always clears weak work`() {
        val player = Player(Tile(3200, 3200))
        player.actionQueue.add(
            PlayerScriptRequest(PlayerScript("weak") {}),
            PlayerActionPriority.WEAK,
        )

        assertFalse(player.closeModal())

        assertEquals(0, player.actionQueue.weakSize)
    }
}
