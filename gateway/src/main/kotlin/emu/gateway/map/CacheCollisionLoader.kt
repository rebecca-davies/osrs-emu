package emu.gateway.map

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.game.pathfinding.CollisionMap

/** Loads a rectangular set of map squares and builds their cache collision baseline. */
class CacheCollisionLoader(
    private val maps: CacheMapRepository,
    private val objects: CacheObjectDefinitionRepository,
) {
    fun load(squareXs: IntRange, squareYs: IntRange): CollisionMap {
        val squares = squareXs.flatMap { squareX -> squareYs.map { squareY -> maps.load(squareX, squareY) } }
        return CacheCollisionMapBuilder.build(squares, objects::get)
    }
}
