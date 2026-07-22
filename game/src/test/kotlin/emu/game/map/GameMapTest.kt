package emu.game.map

import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.testPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class GameMapTest {
    @Test
    fun `movement prepares collision only after crossing a map-square boundary`() {
        val requested = mutableListOf<Tile>()
        val map = GameMap(OpenCollisionMap, requested::add)
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
