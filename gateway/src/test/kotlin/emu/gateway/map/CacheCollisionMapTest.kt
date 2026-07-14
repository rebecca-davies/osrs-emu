package emu.gateway.map

import emu.cache.map.MapSquare
import emu.cache.map.MapTileDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheCollisionMapTest {
    @Test
    fun `loads and caches collision chunks as new map squares are queried`() {
        val requested = mutableListOf<Pair<Int, Int>>()
        val squares = mapOf(
            (50 to 50) to openSquare(50, 50),
            (52 to 50) to openSquare(52, 50),
        )
        val collision = CacheCollisionMap(
            mapSquare = { x, y -> requested += x to y; squares[x to y] },
            objectDefinition = { null },
        )

        assertEquals(0, collision.flagsAt(50 * 64, 50 * 64, 0))
        val afterFirstLoad = requested.size
        assertEquals(0, collision.flagsAt(50 * 64 + 1, 50 * 64, 0))
        assertEquals(afterFirstLoad, requested.size)

        assertFalse(requested.contains(52 to 50))
        assertEquals(0, collision.flagsAt(52 * 64, 50 * 64, 0))
        assertTrue(requested.contains(52 to 50))
        assertEquals(-1, collision.flagsAt(54 * 64, 50 * 64, 0))
    }

    private fun openSquare(x: Int, y: Int): MapSquare =
        MapSquare(x, y, MapTileDecoder.decode(ByteArray(4 * 64 * 64 * 2 + 1)), emptyList())
}
