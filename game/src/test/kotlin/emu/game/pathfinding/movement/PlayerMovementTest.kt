package emu.game.pathfinding.movement

import emu.game.map.Tile
import emu.game.map.GameMap
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.MutableCollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.testPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerMovementTest {
    @Test
    fun `walking consumes one route step per player phase`() {
        val player = testPlayer(Tile(0, 0))
        val movement = player.movement
        val map = GameMap(OpenCollisionMap)
        player.walkTo(Tile(3, 0))

        map.advance(player)
        assertEquals(Tile(1, 0), movement.position)
        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
        movement.finishCycle()
        map.advance(player)

        assertEquals(Tile(2, 0), movement.position)
    }

    @Test
    fun `running consumes two validated route steps per player phase`() {
        val player = testPlayer(Tile(0, 0))
        val movement = player.movement
        val map = GameMap(OpenCollisionMap)
        movement.runEnabled = true
        player.walkTo(Tile(4, 0))

        map.advance(player)

        assertEquals(Tile(2, 0), movement.position)
        assertEquals(MovementUpdate.Run(2, 0), movement.update)
    }

    @Test
    fun `temporary speed applies only to its route`() {
        val player = testPlayer(Tile(0, 0))
        val movement = player.movement
        val map = GameMap(OpenCollisionMap)
        player.walkTo(Tile(2, 0), temporaryRun = true)
        map.advance(player)
        assertEquals(MovementUpdate.Run(2, 0), movement.update)

        player.walkTo(Tile(4, 0))
        map.advance(player)

        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
    }

    @Test
    fun `dynamic obstruction leaves waypoint queued for a later retry`() {
        val collision = MutableCollisionMap()
        val player = testPlayer(Tile(0, 0))
        val movement = player.movement
        val map = GameMap(collision)
        player.walkTo(Tile(2, 0))
        map.resolveRoute(player)
        collision.add(1, 0, 0, CollisionFlag.OBJECT)

        map.advance(player)
        assertEquals(Tile(0, 0), movement.position)
        assertTrue(movement.hasRoute)

        collision.remove(1, 0, 0, CollisionFlag.OBJECT)
        map.advance(player)
        assertEquals(Tile(1, 0), movement.position)
    }

    @Test
    fun `cleanup clears only the visible movement delta`() {
        val player = testPlayer(Tile(0, 0))
        val movement = player.movement
        val map = GameMap(OpenCollisionMap)
        player.walkTo(Tile(2, 0))
        map.advance(player)

        movement.finishCycle()

        assertEquals(MovementUpdate.Idle, movement.update)
        assertTrue(movement.hasRoute)
    }

    @Test
    fun `teleport discards a route and remains visible until cycle cleanup`() {
        val requested = mutableListOf<Tile>()
        val player = testPlayer(Tile(3_127, 3_621))
        val map = GameMap(OpenCollisionMap, requestAreas = requested::add)
        player.walkTo(Tile(3_130, 3_621))
        map.resolveRoute(player)

        player.teleportTo(Tile(2_271, 5_332))
        map.advance(player)

        assertEquals(Tile(2_271, 5_332), player.movement.position)
        assertEquals(MovementUpdate.Teleport(-856, 1_711, 0), player.movement.update)
        assertEquals(listOf(Tile(2_271, 5_332)), requested)
        assertTrue(!player.movement.hasRoute)

        player.finishCycle()
        assertEquals(MovementUpdate.Idle, player.movement.update)
    }
}
