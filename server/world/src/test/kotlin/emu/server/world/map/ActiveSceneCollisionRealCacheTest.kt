package emu.server.world.map

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.store.FlatFileStore
import emu.game.pathfinding.BfsPathfinder
import emu.game.pathfinding.CollisionFlag
import emu.game.pathfinding.Tile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActiveSceneCollisionRealCacheTest {
    @Test
    fun `explicit collision preparation loads a map square beyond the original Lumbridge scene`() {
        val cache = File("../cache-data")
        if (!File(cache, "cache/255/5.dat").isFile) {
            println("SKIP: no rev-239 cache-data")
            return
        }
        val store = FlatFileStore(cache)
        val collision = CacheCollisionMap(
            CacheMapRepository(store),
            CacheObjectDefinitionRepository(store),
        )
        collision.loadAround(Tile(52 * 64, 50 * 64), radius = 0)

        assertNotEquals(-1, collision.flagsAt(52 * 64, 50 * 64, 0))
    }

    @Test
    fun `rev 239 Lumbridge scene contains traversable spawn and cache-derived blockers`() {
        val cache = File("../cache-data")
        if (!File(cache, "cache/255/5.dat").isFile) {
            println("SKIP: no rev-239 cache-data")
            return
        }
        val store = FlatFileStore(cache)
        val collision = CacheCollisionMap(
            CacheMapRepository(store),
            CacheObjectDefinitionRepository(store),
        )
        collision.loadAround(Tile(50 * 64, 50 * 64), radius = 1)

        val blockingTileMask = CollisionFlag.OBJECT or CollisionFlag.FLOOR or CollisionFlag.FLOOR_DECORATION
        assertEquals(0, collision.flagsAt(3222, 3218, 0) and blockingTileMask)
        for (x in 3241..3248) {
            for (y in 3225..3226) {
                assertEquals(0, collision.flagsAt(x, y, 0) and blockingTileMask, "blocked Lumbridge bridge tile $x,$y")
            }
        }
        assertTrue(BfsPathfinder(collision).findPath(Tile(3222, 3218), Tile(3223, 3218)).success)
        var wallTiles = 0
        var objectTiles = 0
        var terrainTiles = 0
        for (plane in 0 until 4) {
            for (x in 49 * 64 until 52 * 64) {
                for (y in 49 * 64 until 52 * 64) {
                    val flags = collision.flagsAt(x, y, plane)
                    if (flags and WALL_MASK != 0) wallTiles++
                    if (flags and CollisionFlag.OBJECT != 0) objectTiles++
                    if (flags and CollisionFlag.FLOOR != 0) terrainTiles++
                }
            }
        }
        assertTrue(wallTiles > 100, "expected real wall collision, got $wallTiles tiles")
        assertTrue(objectTiles > 100, "expected real object collision, got $objectTiles tiles")
        assertTrue(terrainTiles > 100, "expected real terrain collision, got $terrainTiles tiles")
    }

    private companion object {
        const val WALL_MASK =
            CollisionFlag.WALL_NORTH_WEST or
                CollisionFlag.WALL_NORTH or
                CollisionFlag.WALL_NORTH_EAST or
                CollisionFlag.WALL_EAST or
                CollisionFlag.WALL_SOUTH_EAST or
                CollisionFlag.WALL_SOUTH or
                CollisionFlag.WALL_SOUTH_WEST or
                CollisionFlag.WALL_WEST
    }
}
