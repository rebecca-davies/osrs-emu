package emu.game.map

import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.testPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameMapTest {
    @Test
    fun `dumb NPC step uses the south-west footprint and validates its complete leading edge`() {
        val open = GameMap(OpenCollisionMap)

        val first = open.nextDumbNpcStep(Tile(10, 10), size = 2, target = Tile(13, 10))

        assertEquals(Tile(11, 10), first)
        assertNull(open.nextDumbNpcStep(requireNotNull(first), size = 2, target = Tile(13, 10)))

        val blocked =
            GameMap(
                CollisionMap { x, y, _ ->
                    if (x == 12 && y == 10) CollisionFlag.OBJECT else 0
                },
            )
        assertNull(blocked.nextDumbNpcStep(Tile(10, 10), size = 2, target = Tile(20, 10)))
    }

    @Test
    fun `movement prepares collision only after crossing a map-square boundary`() {
        val requested = mutableListOf<Tile>()
        val map = GameMap(OpenCollisionMap, requestAreas = requested::add)
        val player = testPlayer(Tile(63, 32))
        player.walkTo(Tile(64, 32))

        map.advance(player)
        map.advance(player)

        assertEquals(Tile(64, 32), player.movement.position)
        assertEquals(listOf(Tile(64, 32)), requested)
    }

    @Test
    fun `rejected collision request is retained until a later world cycle accepts it`() {
        val requested = mutableListOf<Tile>()
        var accepting = false
        val map =
            GameMap(OpenCollisionMap) { tile ->
                requested += tile
                accepting
            }
        val player = testPlayer(Tile(63, 32))
        player.walkTo(Tile(64, 32))

        map.advance(player)
        map.retryAreaRequests()
        accepting = true
        map.retryAreaRequests()
        map.retryAreaRequests()

        assertEquals(Tile(64, 32), player.movement.position)
        assertEquals(List(3) { Tile(64, 32) }, requested)
    }
}
