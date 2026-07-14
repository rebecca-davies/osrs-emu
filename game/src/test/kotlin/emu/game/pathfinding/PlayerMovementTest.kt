package emu.game.pathfinding

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.GameCycle
import emu.game.runSuspending
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlayerMovementTest {
    @Test
    fun `walking consumes one route step per player cycle`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        movement.routeTo(Tile(3, 0))

        movement.process()
        assertEquals(Tile(1, 0), movement.position)
        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
        movement.finishCycle()
        movement.process()

        assertEquals(Tile(2, 0), movement.position)
    }

    @Test
    fun `running consumes two validated route steps per player cycle`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        movement.runEnabled = true
        movement.routeTo(Tile(4, 0))

        movement.process()

        assertEquals(Tile(2, 0), movement.position)
        assertEquals(MovementUpdate.Run(2, 0), movement.update)
    }

    @Test
    fun `temporary speed applies only to its route`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        movement.routeTo(Tile(2, 0), temporaryRun = true)
        movement.process()
        assertEquals(MovementUpdate.Run(2, 0), movement.update)

        movement.routeTo(Tile(4, 0))
        movement.process()

        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
    }

    @Test
    fun `a redundant current-tile waypoint is skipped safely`() {
        val movement = PlayerMovement(Tile(1, 1), OpenCollisionMap)
        movement.queueRoute(
            PathRoute(
                waypoints = listOf(Tile(1, 1), Tile(2, 1)),
                alternative = false,
                success = true,
            ),
        )

        movement.process()

        assertEquals(Tile(2, 1), movement.position)
        assertEquals(MovementUpdate.Walk(1, 0), movement.update)
    }

    @Test
    fun `dynamic obstruction leaves waypoint queued for a later retry`() {
        val collision = MutableCollisionMap()
        val movement = PlayerMovement(Tile(0, 0), collision)
        movement.routeTo(Tile(2, 0))
        collision.add(1, 0, 0, CollisionFlag.OBJECT)

        movement.process()
        assertEquals(Tile(0, 0), movement.position)
        assertTrue(movement.hasRoute)

        collision.remove(1, 0, 0, CollisionFlag.OBJECT)
        movement.process()
        assertEquals(Tile(1, 0), movement.position)
    }

    @Test
    fun `movement runs in player phase and remains visible until cleanup`() {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        movement.routeTo(Tile(1, 0))
        val observed = mutableListOf<Pair<CyclePhase, MovementUpdate>>()
        val cycle =
            GameCycle(
                movement.cycleProcesses() +
                    CycleProcess(CyclePhase.INFO) { observed += CyclePhase.INFO to movement.update } +
                    CycleProcess(CyclePhase.CLIENT_OUTPUT) {
                        observed += CyclePhase.CLIENT_OUTPUT to movement.update
                    } +
                    CycleProcess(CyclePhase.CLEANUP) {
                        observed += CyclePhase.CLEANUP to movement.update
                    },
            )

        runSuspending { cycle.tick() }

        assertIs<MovementUpdate.Walk>(observed[0].second)
        assertIs<MovementUpdate.Walk>(observed[1].second)
        assertEquals(MovementUpdate.Idle, observed[2].second)
    }
}
