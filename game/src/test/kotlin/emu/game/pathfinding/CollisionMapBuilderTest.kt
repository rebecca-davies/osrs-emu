package emu.game.pathfinding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollisionMapBuilderTest {
    @Test
    fun `allocating a square makes its tiles open while unknown tiles remain blocked`() {
        val collision = CollisionMapBuilder().allocateSquare(50, 50).build()

        assertEquals(0, collision.flagsAt(3_200, 3_200, 0))
        assertEquals(0, collision.flagsAt(3_263, 3_263, 3))
        assertEquals(-1, collision.flagsAt(3_199, 3_200, 0))
    }

    @Test
    fun `straight walls flag both sides of the blocked edge`() {
        val collision = CollisionMapBuilder()
            .allocateSquare(50, 50)
            .blockWall(Tile(3_220, 3_220), LocShape.WALL_STRAIGHT, rotation = 0)
            .build()

        assertTrue(collision.flagsAt(3_220, 3_220, 0) and CollisionFlag.WALL_WEST != 0)
        assertTrue(collision.flagsAt(3_219, 3_220, 0) and CollisionFlag.WALL_EAST != 0)
        assertTrue(!collision.canTravel(3_220, 3_220, 0, -1, 0))
        assertTrue(!collision.canTravel(3_219, 3_220, 0, 1, 0))
    }

    @Test
    fun `rectangular locs and floor decorations block every occupied tile`() {
        val collision = CollisionMapBuilder()
            .allocateSquare(50, 50)
            .blockObject(Tile(3_220, 3_220), width = 2, length = 3)
            .blockFloorDecoration(Tile(3_225, 3_225))
            .blockFloor(Tile(3_226, 3_226))
            .build()

        for (x in 3_220..3_221) {
            for (y in 3_220..3_222) {
                assertTrue(collision.flagsAt(x, y, 0) and CollisionFlag.OBJECT != 0)
            }
        }
        assertTrue(collision.flagsAt(3_225, 3_225, 0) and CollisionFlag.FLOOR_DECORATION != 0)
        assertTrue(collision.flagsAt(3_226, 3_226, 0) and CollisionFlag.FLOOR != 0)
    }

    @Test
    fun `terrain assignment can clear an underlying floor blocker`() {
        val tile = Tile(3_220, 3_220)
        val collision = CollisionMapBuilder()
            .allocateSquare(50, 50)
            .blockFloor(tile)
            .setFloorBlocked(tile, blocked = false)
            .build()

        assertEquals(0, collision.flagsAt(tile.x, tile.y, tile.plane) and CollisionFlag.FLOOR)
    }
}
