package emu.server.game.world.map

import emu.cache.def.EntityOps
import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.PreparedMapSquareLookup
import emu.cache.map.codec.MapTileDecoder
import emu.cache.map.model.MapLocSpawn
import emu.cache.map.model.MapSquare
import emu.cache.store.FlatFileStore
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
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
                blockingMask = 1,
                ops = EntityOps(ops = mapOf(0 to "Enter", 1 to "Join", 2 to "Disable-XP")),
            )
        val repository =
            CacheLocRepository(
                cachedMapSquares = PreparedMapSquareLookup { x, y -> square.takeIf { x == 48 && y == 56 } },
                objectDefinition = { type -> definition.takeIf { type == PORTAL_TYPE } },
            )

        val loc = requireNotNull(repository.find(PORTAL_TYPE, Tile(3_126, 3_620)))

        assertEquals(1, loc.width)
        assertEquals(4, loc.length)
        assertEquals(2, loc.forceApproachFlags)
        assertTrue(loc.supports(option = 1, subOption = 0))
        assertTrue(repository.isCurrent(loc))
        assertSame(loc, repository.find(PORTAL_TYPE, Tile(3_126, 3_620)))
        assertNull(repository.find(PORTAL_TYPE, Tile(3_125, 3_620)))
    }

    @Test
    fun `authoritative placement rejects a missing or replaced prepared loc`() {
        val tiles = MapTileDecoder.decode(ByteArray(4 * 64 * 64 * 2 + 1))
        var prepared: MapSquare? =
            MapSquare(
                squareX = 48,
                squareY = 56,
                tiles = tiles,
                locs = listOf(MapLocSpawn(PORTAL_TYPE, 54, 36, 0, shape = 10, rotation = 1)),
            )
        val definition = ObjectDefinition(id = PORTAL_TYPE, ops = EntityOps(ops = mapOf(0 to "Enter")))
        val repository =
            CacheLocRepository(
                cachedMapSquares =
                    PreparedMapSquareLookup { x, y -> prepared.takeIf { x == 48 && y == 56 } },
                objectDefinition = { type -> definition.takeIf { type == PORTAL_TYPE } },
            )
        val loc = requireNotNull(repository.find(PORTAL_TYPE, Tile(3_126, 3_620)))

        prepared = null
        assertFalse(repository.isCurrent(loc))

        prepared =
            MapSquare(
                squareX = 48,
                squareY = 56,
                tiles = tiles,
                locs = listOf(MapLocSpawn(PORTAL_TYPE, 54, 36, 0, shape = 10, rotation = 2)),
            )
        assertFalse(repository.isCurrent(loc))
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

        val repository = CacheLocRepository(maps, objects)
        val portal = requireNotNull(repository.find(PORTAL_TYPE, Tile(3_126, 3_620)))
        val exit = requireNotNull(repository.find(INFERNO_EXIT_TYPE, Tile(2_269, 5_325)))

        assertEquals(1, portal.width)
        assertEquals(4, portal.length)
        assertTrue(portal.supports(option = 1, subOption = 0))
        assertEquals(5, exit.width)
        assertEquals(4, exit.length)
        assertTrue(exit.supports(option = 1, subOption = 0))
        val blocked = CollisionFlag.OBJECT or CollisionFlag.FLOOR or CollisionFlag.FLOOR_DECORATION
        assertEquals(0, collision.flagsAt(2_271, 5_332, 0) and blocked)
    }

    private companion object {
        const val PORTAL_TYPE = 26_642
        const val INFERNO_EXIT_TYPE = 30_283
    }
}
