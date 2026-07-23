package emu.server.game.world.map

import emu.cache.def.EntityOps
import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.PreparedMapSquareLookup
import emu.cache.map.model.MapLocSpawn
import emu.cache.map.model.MapSquare
import emu.cache.map.model.MapTileFlags
import emu.game.loc.Loc
import emu.game.loc.LocRepository
import emu.game.map.Tile
import java.util.LinkedHashMap

/** Exposes already-prepared rev-239 static locs to authoritative world input handling. */
class CacheLocRepository(
    private val cachedMapSquares: PreparedMapSquareLookup,
    private val objectDefinition: (Int) -> ObjectDefinition?,
) : LocRepository {
    private val resolvedLocs =
        object : LinkedHashMap<Long, Loc>(MAX_CACHED_LOCS, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Loc>?): Boolean =
                size > MAX_CACHED_LOCS
        }

    constructor(
        maps: CacheMapRepository,
        objects: CacheObjectDefinitionRepository,
    ) : this(maps, objects::get)

    override fun find(type: Int, tile: Tile): Loc? {
        if (type !in 0..0xFFFF) return null
        val squareX = tile.x shr MAP_SQUARE_SHIFT
        val squareY = tile.y shr MAP_SQUARE_SHIFT
        val square = cachedMapSquares.findPrepared(squareX, squareY) ?: return null
        val placement =
            square.findLoc(type, tile.plane, tile.x and MAP_SQUARE_MASK, tile.y and MAP_SQUARE_MASK)
                ?: return null
        val key = placementKey(type, tile)
        resolvedLocs[key]?.let { cached ->
            if (cached.shape == placement.shape && cached.angle == placement.rotation) return cached
            resolvedLocs.remove(key)
        }
        val definition = objectDefinition(type) ?: return null
        return placement.toLoc(tile, definition).also { resolvedLocs[key] = it }
    }

    override fun isCurrent(loc: Loc): Boolean {
        val tile = loc.tile
        val square =
            cachedMapSquares.findPrepared(tile.x shr MAP_SQUARE_SHIFT, tile.y shr MAP_SQUARE_SHIFT)
                ?: return false
        val placement =
            square.findLoc(loc.type, tile.plane, tile.x and MAP_SQUARE_MASK, tile.y and MAP_SQUARE_MASK)
                ?: return false
        return placement.shape == loc.shape && placement.rotation == loc.angle
    }

    private fun MapLocSpawn.toLoc(tile: Tile, definition: ObjectDefinition): Loc {
        var width = definition.sizeX ?: DEFAULT_SIZE
        var length = definition.sizeY ?: DEFAULT_SIZE
        if (rotation == 1 || rotation == 3) {
            val previousWidth = width
            width = length
            length = previousWidth
        }
        return Loc(
            type = id,
            tile = tile,
            shape = shape,
            angle = rotation,
            width = width,
            length = length,
            forceApproachFlags = definition.blockingMask.rotate(rotation),
            options = definition.ops.optionSlots(),
            subOptions = definition.ops.subOptionSlots(),
        )
    }

    private fun EntityOps.optionSlots(): Set<Int> =
        buildSet {
            ops.keys.forEach { add(it + 1) }
            subOps.forEach { add(it.index + 1) }
        }

    private fun EntityOps.subOptionSlots(): Map<Int, Set<Int>> =
        subOps
            .groupBy { it.index + 1 }
            .mapValues { (_, entries) -> entries.mapTo(linkedSetOf(), EntityOps.SubOp::subId) }

    private fun Int?.rotate(angle: Int): Int {
        val flags = (this ?: 0) and ACCESS_MASK
        if (angle == 0) return flags
        return (flags shl angle and ACCESS_MASK) or (flags ushr (DIRECTION_COUNT - angle))
    }

    private fun placementKey(type: Int, tile: Tile): Long =
        (type.toLong() shl TYPE_SHIFT) or
            (tile.plane.toLong() shl PLANE_SHIFT) or
            (tile.x.toLong() shl X_SHIFT) or
            tile.y.toLong()

    private companion object {
        const val MAP_SQUARE_SHIFT = 6
        const val MAP_SQUARE_MASK = MapTileFlags.MAP_SQUARE_SIZE - 1
        const val DEFAULT_SIZE = 1
        const val DIRECTION_COUNT = 4
        const val ACCESS_MASK = (1 shl DIRECTION_COUNT) - 1
        const val WORLD_COORDINATE_BITS = 14
        const val X_SHIFT = WORLD_COORDINATE_BITS
        const val PLANE_SHIFT = WORLD_COORDINATE_BITS * 2
        const val TYPE_SHIFT = PLANE_SHIFT + 2
        const val MAX_CACHED_LOCS = 4_096
        const val LOAD_FACTOR = 0.75f
    }
}
