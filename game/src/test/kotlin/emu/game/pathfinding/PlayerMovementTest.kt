package emu.game.pathfinding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerMovementTest {
    @Test
    fun `walking consumes one route step per player phase`() {
        val movement = PlayerMovement(Tile(0, 0))
        val process = PlayerMovementProcess(OpenCollisionMap)
        process.routeTo(movement, Tile(3, 0))

        process.process(movement)
        assertEquals(Tile(1, 0), movement.position)
        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
        movement.finishCycle()
        process.process(movement)

        assertEquals(Tile(2, 0), movement.position)
    }

    @Test
    fun `running consumes two validated route steps per player phase`() {
        val movement = PlayerMovement(Tile(0, 0))
        val process = PlayerMovementProcess(OpenCollisionMap)
        movement.runEnabled = true
        process.routeTo(movement, Tile(4, 0))

        process.process(movement)

        assertEquals(Tile(2, 0), movement.position)
        assertEquals(MovementUpdate.Run(2, 0), movement.update)
    }

    @Test
    fun `temporary speed applies only to its route`() {
        val movement = PlayerMovement(Tile(0, 0))
        val process = PlayerMovementProcess(OpenCollisionMap)
        process.routeTo(movement, Tile(2, 0), temporaryRun = true)
        process.process(movement)
        assertEquals(MovementUpdate.Run(2, 0), movement.update)

        process.routeTo(movement, Tile(4, 0))
        process.process(movement)

        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
    }

    @Test
    fun `dynamic obstruction leaves waypoint queued for a later retry`() {
        val collision = MutableCollisionMap()
        val movement = PlayerMovement(Tile(0, 0))
        val process = PlayerMovementProcess(collision)
        process.routeTo(movement, Tile(2, 0))
        collision.add(1, 0, 0, CollisionFlag.OBJECT)

        process.process(movement)
        assertEquals(Tile(0, 0), movement.position)
        assertTrue(movement.hasRoute)

        collision.remove(1, 0, 0, CollisionFlag.OBJECT)
        process.process(movement)
        assertEquals(Tile(1, 0), movement.position)
    }

    @Test
    fun `cleanup clears only the visible movement delta`() {
        val movement = PlayerMovement(Tile(0, 0))
        val process = PlayerMovementProcess(OpenCollisionMap)
        process.routeTo(movement, Tile(2, 0))
        process.process(movement)

        movement.finishCycle()

        assertEquals(MovementUpdate.Idle, movement.update)
        assertTrue(movement.hasRoute)
    }
}
