package emu.game.pathfinding

import emu.game.cycle.GameCycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerRouteRequestQueueTest {
    @Test
    fun `bounded mailbox rejects input beyond capacity`() {
        val requests = PlayerRouteRequestQueue(capacity = 1)

        assertTrue(requests.submit(1, 0, keyCombination = 0))
        assertFalse(requests.submit(2, 0, keyCombination = 0))
    }

    @Test
    fun `route requests drain in client-input before player movement`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        val requests = PlayerRouteRequestQueue(maxPerCycle = 1)
        val cycle = GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses())
        requests.submit(1, 0, keyCombination = 0)
        requests.submit(3, 0, keyCombination = 0)

        cycle.tick()
        assertEquals(Tile(1, 0), movement.position)
        cycle.tick()

        assertEquals(Tile(2, 0), movement.position)
    }

    @Test
    fun `route acquires the authoritative plane when the world drains it`() {
        val movement = PlayerMovement(Tile(0, 0, plane = 2), OpenCollisionMap)
        val requests = PlayerRouteRequestQueue()
        val cycle = GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses())
        requests.submit(1, 0, keyCombination = 0)

        cycle.tick()

        assertEquals(Tile(1, 0, plane = 2), movement.position)
    }

    @Test
    fun `control reverses run mode for the submitted route`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        val requests = PlayerRouteRequestQueue()
        val cycle = GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses())
        requests.submit(3, 0, keyCombination = 1)

        cycle.tick()

        assertEquals(Tile(2, 0), movement.position)
        assertFalse(movement.runEnabled)
    }

    @Test
    fun `malformed world coordinates are discarded on the world cycle`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        val requests = PlayerRouteRequestQueue()
        val cycle = GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses())
        requests.submit(0x4000, 0, keyCombination = 0)

        cycle.tick()

        assertEquals(Tile(0, 0), movement.position)
    }
}
