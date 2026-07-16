package emu.game.pathfinding.route

import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.MutableCollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.PlayerMovement
import emu.game.pathfinding.movement.PlayerMovementProcess
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BfsPathfinderTest {
    @Test
    fun `open straight path is compressed to its destination turnpoint`() {
        val pathfinder = BfsPathfinder(OpenCollisionMap)

        val route = pathfinder.findPath(Tile(3200, 3200), Tile(3205, 3200))

        assertTrue(route.success)
        assertFalse(route.alternative)
        assertEquals(listOf(Tile(3205, 3200)), route.waypoints)
    }

    @Test
    fun `breadth first route detours around object collision`() {
        val collision = MutableCollisionMap()
        collision.add(3201, 3200, 0, CollisionFlag.OBJECT)
        val movement = PlayerMovement(Tile(3200, 3200))
        val process = PlayerMovementProcess(collision)

        val route = process.routeTo(movement, Tile(3202, 3200))
        repeat(10) { process.process(movement) }

        assertTrue(route.success)
        assertEquals(Tile(3202, 3200), movement.position)
    }

    @Test
    fun `diagonal cannot squeeze through two blocked orthogonal tiles`() {
        val collision = MutableCollisionMap(defaultFlag = -1)
        collision[0, 0, 0] = 0
        collision[1, 1, 0] = 0
        val pathfinder = BfsPathfinder(collision, moveNear = false)

        val route = pathfinder.findPath(Tile(0, 0), Tile(1, 1))

        assertFalse(route.success)
        assertEquals(emptyList(), route.waypoints)
    }

    @Test
    fun `blocked destination selects Blurite move-near alternative`() {
        val collision = MutableCollisionMap()
        collision.add(10, 10, 0, CollisionFlag.OBJECT)
        val pathfinder = BfsPathfinder(collision)

        val route = pathfinder.findPath(Tile(5, 5), Tile(10, 10))
        val endpoint = route.waypoints.last()

        assertTrue(route.success)
        assertTrue(route.alternative)
        assertTrue(abs(endpoint.x - 10) <= 1 && abs(endpoint.y - 10) <= 1)
        assertFalse(endpoint == Tile(10, 10))
    }

    @Test
    fun `different planes never produce a route`() {
        val route = BfsPathfinder(OpenCollisionMap).findPath(Tile(0, 0, 0), Tile(1, 1, 1))

        assertFalse(route.success)
    }
}
