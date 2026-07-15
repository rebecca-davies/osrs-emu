package emu.server.game.map

import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.MapSquare
import emu.game.pathfinding.CollisionMap
import java.util.concurrent.ConcurrentHashMap

/**
 * World collision decoded lazily in 64x64 chunks from the rev-239 cache.
 *
 * Each chunk includes its eight neighbouring map squares while building, so walls and multi-tile
 * locs crossing a square boundary contribute their flags to the requested centre chunk. Decoded
 * chunks are shared safely by every connection and retained for subsequent path searches.
 */
class CacheCollisionMap(
    private val mapSquare: (Int, Int) -> MapSquare?,
    private val objectDefinition: (Int) -> ObjectDefinition?,
) : CollisionMap {
    constructor(
        maps: CacheMapRepository,
        objects: CacheObjectDefinitionRepository,
    ) : this(maps::loadOrNull, objects::get)

    private val chunks = ConcurrentHashMap<Int, CollisionChunk>()

    override fun flagsAt(x: Int, y: Int, plane: Int): Int {
        if (x !in WORLD_COORDINATES || y !in WORLD_COORDINATES || plane !in PLANES) return -1
        val squareX = x shr MAP_SQUARE_SHIFT
        val squareY = y shr MAP_SQUARE_SHIFT
        val chunk = chunks.computeIfAbsent(squareX shl 8 or squareY) {
            loadChunk(squareX, squareY)
        }
        return chunk.flagsAt(x and MAP_SQUARE_MASK, y and MAP_SQUARE_MASK, plane)
    }

    private fun loadChunk(squareX: Int, squareY: Int): CollisionChunk {
        if (mapSquare(squareX, squareY) == null) return CollisionChunk.BLOCKED
        val neighbours = buildList {
            for (x in squareX - 1..squareX + 1) {
                for (y in squareY - 1..squareY + 1) {
                    mapSquare(x, y)?.let(::add)
                }
            }
        }
        val collision = CacheCollisionMapBuilder.build(neighbours, objectDefinition)
        val flags = IntArray(CHUNK_TILE_COUNT)
        val baseX = squareX shl MAP_SQUARE_SHIFT
        val baseY = squareY shl MAP_SQUARE_SHIFT
        for (plane in PLANES) {
            for (localX in 0 until MAP_SQUARE_SIZE) {
                for (localY in 0 until MAP_SQUARE_SIZE) {
                    flags[index(localX, localY, plane)] = collision.flagsAt(baseX + localX, baseY + localY, plane)
                }
            }
        }
        return CollisionChunk(flags)
    }

    private class CollisionChunk(private val flags: IntArray?) {
        fun flagsAt(localX: Int, localY: Int, plane: Int): Int =
            flags?.get(index(localX, localY, plane)) ?: -1

        companion object {
            val BLOCKED = CollisionChunk(null)
        }
    }

    private companion object {
        const val MAP_SQUARE_SHIFT = 6
        const val MAP_SQUARE_SIZE = 1 shl MAP_SQUARE_SHIFT
        const val MAP_SQUARE_MASK = MAP_SQUARE_SIZE - 1
        const val CHUNK_TILE_COUNT = 4 * MAP_SQUARE_SIZE * MAP_SQUARE_SIZE
        val WORLD_COORDINATES = 0..0x3FFF
        val PLANES = 0..3

        fun index(localX: Int, localY: Int, plane: Int): Int =
            (plane * MAP_SQUARE_SIZE + localX) * MAP_SQUARE_SIZE + localY
    }
}
