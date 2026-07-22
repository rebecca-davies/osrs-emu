package emu.server.game.world.map

import emu.cache.def.EntityOps
import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.codec.MapTileDecoder
import emu.cache.map.model.MapLocSpawn
import emu.cache.map.model.MapSquare
import emu.cache.store.FlatFileStore
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheLocRepositoryTest {
    @Test
    fun `prepared cache placement exposes its rotated footprint and options`() {
        val square =
            MapSquare(
                squareX = 48,
                squareY = 56,
                tiles = MapTileDecoder.decode(ByteArray(4 * 64 * 64 * 2 + 1)),
                locs = listOf(MapLocSpawn(PORTAL_TYPE, 54, 36, 0, shape = 10, rotation = 1)),
            )
        val definition =
            ObjectDefinition(
                id = PORTAL_TYPE,
                sizeX = 4,
                sizeY = 1,
                ops = EntityOps(ops = mapOf(0 to "Enter", 1 to "Join", 2 to "Disable-XP")),
            )
        val repository =
            CacheLocRepository(
                cachedMapSquare = { x, y -> square.takeIf { x == 48 && y == 56 } },
                objectDefinition = { type -> definition.takeIf { type == PORTAL_TYPE } },
            )

        val loc = requireNotNull(repository.find(PORTAL_TYPE, Tile(3_126, 3_620)))

        assertEquals(1, loc.width)
        assertEquals(4, loc.length)
        assertTrue(loc.supports(option = 1, subOption = 0))
        assertTrue(loc.isAdjacentTo(Tile(3_127, 3_621)))
        assertNull(repository.find(PORTAL_TYPE, Tile(3_125, 3_620)))
    }

    @Test
    fun `rev 239 cache contains the configured portal and safe Inferno arrival`() {
        val cache = File("../../cache-data")
        if (!File(cache, "cache/255/5.dat").isFile) return
        val store = FlatFileStore(cache)
        val maps = CacheMapRepository(store)
        val objects = CacheObjectDefinitionRepository(store)
        val collision = CacheCollisionMap(maps, objects)
        collision.loadAround(Tile(3_127, 3_621), radius = 1)
        collision.loadAround(Tile(2_271, 5_332), radius = 1)

        val portal = requireNotNull(CacheLocRepository(maps, objects).find(PORTAL_TYPE, Tile(3_126, 3_620)))

        assertEquals(1, portal.width)
        assertEquals(4, portal.length)
        assertTrue(portal.supports(option = 1, subOption = 0))
        val blocked = CollisionFlag.OBJECT or CollisionFlag.FLOOR or CollisionFlag.FLOOR_DECORATION
        assertEquals(0, collision.flagsAt(2_271, 5_332, 0) and blocked)
    }

    private companion object {
        const val PORTAL_TYPE = 26_642
    }
}
