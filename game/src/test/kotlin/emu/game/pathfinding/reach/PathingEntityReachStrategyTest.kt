package emu.game.pathfinding.reach

import emu.game.map.GameMap
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathingEntityReachStrategyTest {
    private val target = Tile(10, 10)

    @Test
    fun `pathing entity reach accepts cardinal boundary but not overlap or diagonals`() {
        val map = GameMap(OpenCollisionMap)

        assertTrue(map.canReachEntity(Tile(9, 10), target, targetSize = 2))
        assertTrue(map.canReachEntity(Tile(12, 11), target, targetSize = 2))
        assertFalse(map.canReachEntity(Tile(10, 10), target, targetSize = 2))
        assertFalse(map.canReachEntity(Tile(9, 9), target, targetSize = 2))
    }

    @Test
    fun `pathing entity reach cannot cross the source tile wall`() {
        val collision = CollisionMap { x, y, _ -> if (x == 9 && y == 10) CollisionFlag.WALL_EAST else 0 }
        val map = GameMap(collision)

        assertFalse(map.canReachEntity(Tile(9, 10), target, targetSize = 2))
        assertTrue(map.canReachEntity(Tile(12, 10), target, targetSize = 2))
    }
}
