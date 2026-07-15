package emu.game.pathfinding

import emu.game.cycle.GameCycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayerRouteRequestQueueTest {
    @Test
    fun `size one mailbox replaces an older destination with the latest click`() {
        val requests = PlayerRouteRequestQueue()

        assertEquals(RouteRequestAdmission.QUEUED, requests.submit(0, 3, keyCombination = 0))
        assertEquals(RouteRequestAdmission.REPLACED, requests.submit(3, 0, keyCombination = 0))
        assertEquals(RouteRequestMetrics(submitted = 2, replaced = 1, rejected = 0, processed = 0), requests.metrics())
    }

    @Test
    fun `only the latest destination searches once in client-input before movement`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        val requests = PlayerRouteRequestQueue()
        val cycle = GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses())
        requests.submit(0, 3, keyCombination = 0)
        requests.submit(3, 0, keyCombination = 0)

        cycle.tick()
        assertEquals(Tile(1, 0), movement.position)
        assertEquals(1, requests.metrics().processed)
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
        assertEquals(RouteRequestAdmission.REJECTED, requests.submit(0x4000, 0, keyCombination = 0))

        cycle.tick()

        assertEquals(Tile(0, 0), movement.position)
        assertEquals(RouteRequestMetrics(submitted = 1, replaced = 0, rejected = 1, processed = 0), requests.metrics())
    }
}
