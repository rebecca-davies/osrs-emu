package emu.server.game.world.map

import emu.cache.def.EntityOps
import emu.cache.def.ObjectDefinition
import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.map.model.MapLocSpawn
import emu.cache.map.model.MapSquare
import emu.cache.map.model.MapTileFlags
import emu.game.loc.Loc
import emu.game.loc.LocRepository
import emu.game.map.Tile

/** Exposes already-prepared rev-239 static locs to authoritative world input handling. */
class CacheLocRepository(
    private val cachedMapSquare: (Int, Int) -> MapSquare?,
    private val objectDefinition: (Int) -> ObjectDefinition?,
) : LocRepository {
    constructor(
        maps: CacheMapRepository,
        objects: CacheObjectDefinitionRepository,
    ) : this(maps::cachedOrNull, objects::get)

    override fun find(type: Int, tile: Tile): Loc? {
        if (type !in 0..0xFFFF) return null
        val squareX = tile.x shr MAP_SQUARE_SHIFT
        val squareY = tile.y shr MAP_SQUARE_SHIFT
        val square = cachedMapSquare(squareX, squareY) ?: return null
        val placement =
            square.findLoc(type, tile.plane, tile.x and MAP_SQUARE_MASK, tile.y and MAP_SQUARE_MASK)
                ?: return null
        val definition = objectDefinition(type) ?: return null
        return placement.toLoc(tile, definition)
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

    private companion object {
        const val MAP_SQUARE_SHIFT = 6
        const val MAP_SQUARE_MASK = MapTileFlags.MAP_SQUARE_SIZE - 1
        const val DEFAULT_SIZE = 1
    }
}
