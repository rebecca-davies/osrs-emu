package emu.game.pathfinding.reach

import emu.game.loc.Loc
import emu.game.map.GameMap
import emu.game.map.Tile
import emu.game.pathfinding.collision.LocShape
import emu.game.pathfinding.collision.OpenCollisionMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocReachStrategyTest {
    private val map = GameMap(OpenCollisionMap)

    @Test
    fun `rectangle access flags reject only the configured side`() {
        val loc = loc(shape = LocShape.CENTREPIECE_STRAIGHT, forceApproachFlags = WEST)

        assertFalse(map.canReachLoc(Tile(9, 10), loc))
        assertTrue(map.canReachLoc(Tile(11, 10), loc))
        assertTrue(map.canReachLoc(Tile(10, 9), loc))
    }

    @Test
    fun `straight wall follows its cache angle instead of rectangular adjacency`() {
        val loc = loc(shape = LocShape.WALL_STRAIGHT, angle = 0)

        assertTrue(map.canReachLoc(Tile(9, 10), loc))
        assertTrue(map.canReachLoc(Tile(10, 11), loc))
        assertFalse(map.canReachLoc(Tile(11, 10), loc))
    }

    @Test
    fun `unsupported roof shape can only be operated from its own tile`() {
        val loc = loc(shape = LocShape.ROOF_STRAIGHT)

        assertTrue(map.canReachLoc(Tile(10, 10), loc))
        assertFalse(map.canReachLoc(Tile(10, 11), loc))
    }

    private fun loc(
        shape: Int,
        angle: Int = 0,
        forceApproachFlags: Int = 0,
    ) =
        Loc(
            type = 1,
            tile = Tile(10, 10),
            shape = shape,
            angle = angle,
            width = 1,
            length = 1,
            forceApproachFlags = forceApproachFlags,
            options = setOf(1),
        )

    private companion object {
        const val WEST = 0x8
    }
}
