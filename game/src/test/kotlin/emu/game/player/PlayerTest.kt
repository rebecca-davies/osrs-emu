package emu.game.player

import emu.game.content.player.PlayerVarpCatalog
import emu.game.map.Tile
import emu.game.queue.PlayerActionPriority
import emu.game.script.execution.PlayerScript
import emu.game.script.execution.PlayerScriptRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerTest {
    @Test
    fun `saved run mode hydrates movement state`() {
        val running =
            testPlayer(
                Tile(3200, 3200),
                savedVarps = mapOf(PlayerVarpCatalog.RUN_MODE.id to 1),
            )
        val walking = testPlayer(Tile(3200, 3200))

        assertTrue(running.movement.runEnabled)
        assertFalse(walking.movement.runEnabled)
    }

    @Test
    fun `closing a modal always clears weak work`() {
        val player = testPlayer(Tile(3200, 3200))
        player.actionQueue.add(
            PlayerScriptRequest(PlayerScript("weak") {}),
            PlayerActionPriority.WEAK,
        )

        assertFalse(player.closeModal())

        assertEquals(0, player.actionQueue.weakSize)
    }

    @Test
    fun `animation request remains available until cycle cleanup`() {
        val player = testPlayer(Tile(3200, 3200))

        player.playAnimation(id = 1234, delay = 2)

        assertEquals(PlayerAnimation(1234, 2), player.animationUpdate)
        player.finishCycle()
        assertNull(player.animationUpdate)
    }

    @Test
    fun `animation request accepts stop and rejects values outside the wire range`() {
        val player = testPlayer(Tile(3200, 3200))

        player.playAnimation(-1)
        assertEquals(PlayerAnimation(-1), player.animationUpdate)
        assertFailsWith<IllegalArgumentException> { player.playAnimation(0xFFFF) }
        assertFailsWith<IllegalArgumentException> { player.playAnimation(1, delay = 0x100) }
    }

    @Test
    fun `modal close requests coalesce until the player queue point consumes them`() {
        val player = testPlayer(Tile(3200, 3200))

        assertFalse(player.consumeModalCloseRequest())
        player.requestModalClose()
        player.requestModalClose()

        assertTrue(player.consumeModalCloseRequest())
        assertFalse(player.consumeModalCloseRequest())
    }

    @Test
    fun `idle logout remains distinct until the logout phase consumes it`() {
        val player = testPlayer(Tile(3200, 3200))

        player.requestIdleLogout()

        assertTrue(player.idleLogoutRequested)
        assertFalse(player.logoutRequested)
        assertTrue(player.beginLogout())
        assertFalse(player.idleLogoutRequested)
        assertTrue(player.loggingOut)
    }
}
