package emu.server.game.world.map

import emu.cache.map.codec.MapTileDecoder
import emu.cache.map.model.MapSquare
import emu.game.map.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheCollisionMapTest {
    @Test
    fun `world lookup never loads cache data`() {
        val requested = mutableListOf<Pair<Int, Int>>()
        val squares = mapOf(
            (50 to 50) to openSquare(50, 50),
        )
        val collision = CacheCollisionMap(
            mapSquare = { x, y -> requested += x to y; squares[x to y] },
            objectDefinition = { null },
        )

        assertEquals(-1, collision.flagsAt(50 * 64, 50 * 64, 0))
        assertTrue(requested.isEmpty())

        collision.loadAround(Tile(50 * 64, 50 * 64), radius = 0)

        assertEquals(0, collision.flagsAt(50 * 64, 50 * 64, 0))
        val afterFirstLoad = requested.size
        assertEquals(0, collision.flagsAt(50 * 64 + 1, 50 * 64, 0))
        assertEquals(afterFirstLoad, requested.size)
    }

    @Test
    fun `explicit preparation caches every requested map square`() {
        val requested = mutableListOf<Pair<Int, Int>>()
        val collision = CacheCollisionMap(
            mapSquare = { x, y -> requested += x to y; openSquare(x, y) },
            objectDefinition = { null },
        )

        collision.loadAround(Tile(50 * 64, 50 * 64), radius = 1)
        val afterPreparation = requested.size

        assertEquals(0, collision.flagsAt(51 * 64, 50 * 64, 0))
        assertEquals(-1, collision.flagsAt(52 * 64, 50 * 64, 0))
        assertEquals(afterPreparation, requested.size)
        assertTrue(collision.isLoadedAround(Tile(50 * 64, 50 * 64), radius = 1))
    }

    private fun openSquare(x: Int, y: Int): MapSquare =
        MapSquare(x, y, MapTileDecoder.decode(ByteArray(4 * 64 * 64 * 2 + 1)), emptyList())
}
