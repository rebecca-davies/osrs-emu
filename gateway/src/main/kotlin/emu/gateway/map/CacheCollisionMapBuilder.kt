package emu.gateway.map

import emu.cache.def.ObjectDefinition
import emu.cache.map.MapLocSpawn
import emu.cache.map.MapSquare
import emu.cache.map.MapTileFlags
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.CollisionMapBuilder
import emu.game.pathfinding.LocShape
import emu.game.pathfinding.Tile

/** Converts a decoded cache map-square baseline into walking collision flags. */
object CacheCollisionMapBuilder {
    fun build(
        squares: List<MapSquare>,
        objectDefinition: (Int) -> ObjectDefinition?,
    ): CollisionMap {
        val collision = CollisionMapBuilder()
        for (square in squares) collision.allocateSquare(square.squareX, square.squareY)
        for (square in squares) addTerrain(collision, square)
        for (square in squares) addLocs(collision, square, objectDefinition)
        return collision.build()
    }

    private fun addTerrain(collision: CollisionMapBuilder, square: MapSquare) {
        val baseX = square.squareX * MapTileFlags.MAP_SQUARE_SIZE
        val baseY = square.squareY * MapTileFlags.MAP_SQUARE_SIZE
        for (plane in 0 until MapTileFlags.PLANE_COUNT) {
            for (localX in 0 until MapTileFlags.MAP_SQUARE_SIZE) {
                for (localY in 0 until MapTileFlags.MAP_SQUARE_SIZE) {
                    val flags = square.tiles[localX, localY, plane]
                    if (flags and MapTileFlags.BLOCK_MOVEMENT == 0) continue
                    val resolvedPlane = if (flags and MapTileFlags.LINK_BELOW != 0) plane - 1 else plane
                    if (resolvedPlane >= 0) collision.blockFloor(Tile(baseX + localX, baseY + localY, resolvedPlane))
                }
            }
        }
    }

    private fun addLocs(
        collision: CollisionMapBuilder,
        square: MapSquare,
        objectDefinition: (Int) -> ObjectDefinition?,
    ) {
        val baseX = square.squareX * MapTileFlags.MAP_SQUARE_SIZE
        val baseY = square.squareY * MapTileFlags.MAP_SQUARE_SIZE
        for (loc in square.locs) {
            val plane = visualPlane(square, loc)
            if (plane < 0) continue
            val definition = requireNotNull(objectDefinition(loc.id)) {
                "missing object definition ${loc.id} at ${square.squareX},${square.squareY}"
            }
            addLoc(collision, Tile(baseX + loc.localX, baseY + loc.localY, plane), loc, definition)
        }
    }

    private fun visualPlane(square: MapSquare, loc: MapLocSpawn): Int {
        val tileFlags = square.tiles[loc.localX, loc.localY, loc.plane]
        val tileAboveFlags = if (loc.plane == MapTileFlags.PLANE_COUNT - 1) {
            tileFlags
        } else {
            square.tiles[loc.localX, loc.localY, loc.plane + 1]
        }
        val resolvedFlags = if (tileAboveFlags and MapTileFlags.LINK_BELOW != 0) tileAboveFlags else tileFlags
        return if (resolvedFlags and MapTileFlags.LINK_BELOW != 0) loc.plane - 1 else loc.plane
    }

    private fun addLoc(
        collision: CollisionMapBuilder,
        tile: Tile,
        loc: MapLocSpawn,
        definition: ObjectDefinition,
    ) {
        val blockWalk = definition.interactType ?: DEFAULT_BLOCK_WALK
        if (blockWalk == 0) return

        var width = definition.sizeX ?: DEFAULT_SIZE
        var length = definition.sizeY ?: DEFAULT_SIZE
        if (loc.rotation == 1 || loc.rotation == 3) {
            val previousWidth = width
            width = length
            length = previousWidth
        }

        when {
            loc.shape == LocShape.GROUND_DECORATION -> {
                if (blockWalk == GROUND_DECOR_BLOCK_WALK) collision.blockFloorDecoration(tile)
            }
            loc.shape in WALL_SHAPES -> collision.blockWall(tile, loc.shape, loc.rotation)
            loc.shape == LocShape.WALL_DIAGONAL ||
                loc.shape == LocShape.CENTREPIECE_STRAIGHT ||
                loc.shape == LocShape.CENTREPIECE_DIAGONAL ||
                loc.shape >= LocShape.ROOF_STRAIGHT -> collision.blockObject(tile, width, length)
        }
    }

    private val WALL_SHAPES = setOf(
        LocShape.WALL_STRAIGHT,
        LocShape.WALL_DIAGONAL_CORNER,
        LocShape.WALL_L,
        LocShape.WALL_SQUARE_CORNER,
    )
    private const val DEFAULT_BLOCK_WALK = 2
    private const val GROUND_DECOR_BLOCK_WALK = 1
    private const val DEFAULT_SIZE = 1
}
