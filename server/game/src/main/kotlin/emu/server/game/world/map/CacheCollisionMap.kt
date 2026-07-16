package emu.server.game.world.map

import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.model.MapSquare
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Prepared rev-239 cache collision exposed as non-blocking world lookups.
 *
 * Each chunk includes its eight neighbouring map squares while building, so walls and multi-tile
 * locs crossing a square boundary contribute their flags to the requested centre chunk. Missing
 * or unprepared chunks are blocked; cache reads occur only through explicit preparation.
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
        val chunk = chunks[squareKey(x shr MAP_SQUARE_SHIFT, y shr MAP_SQUARE_SHIFT)] ?: return -1
        return chunk.flagsAt(x and MAP_SQUARE_MASK, y and MAP_SQUARE_MASK, plane)
    }

    internal fun loadAround(position: Tile, radius: Int) {
        require(radius >= 0) { "collision load radius must not be negative" }
        val centreX = position.x shr MAP_SQUARE_SHIFT
        val centreY = position.y shr MAP_SQUARE_SHIFT
        for (squareX in squareRange(centreX, radius)) {
            for (squareY in squareRange(centreY, radius)) {
                chunks.computeIfAbsent(squareKey(squareX, squareY)) {
                    loadChunk(squareX, squareY)
                }
            }
        }
    }

    internal fun isLoadedAround(position: Tile, radius: Int): Boolean {
        require(radius >= 0) { "collision load radius must not be negative" }
        val centreX = position.x shr MAP_SQUARE_SHIFT
        val centreY = position.y shr MAP_SQUARE_SHIFT
        return squareRange(centreX, radius).all { squareX ->
            squareRange(centreY, radius).all { squareY ->
                chunks.containsKey(squareKey(squareX, squareY))
            }
        }
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
        const val MAX_MAP_SQUARE = 255
        val WORLD_COORDINATES = 0..0x3FFF
        val PLANES = 0..3

        fun squareKey(squareX: Int, squareY: Int): Int = squareX shl 8 or squareY

        fun squareRange(centre: Int, radius: Int): IntRange =
            (centre - radius).coerceAtLeast(0)..(centre + radius).coerceAtMost(MAX_MAP_SQUARE)

        fun index(localX: Int, localY: Int, plane: Int): Int =
            (plane * MAP_SQUARE_SIZE + localX) * MAP_SQUARE_SIZE + localY
    }
}
