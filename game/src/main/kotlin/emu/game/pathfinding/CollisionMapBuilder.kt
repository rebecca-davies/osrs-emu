package emu.game.pathfinding

/** Builds a sparse collision map from allocated map squares, terrain rules, and static locs. */
class CollisionMapBuilder(
    private val collision: MutableCollisionMap = MutableCollisionMap(defaultFlag = -1),
) {
    /** Allocates every tile and plane in one 64x64 map square as initially traversable. */
    fun allocateSquare(squareX: Int, squareY: Int): CollisionMapBuilder = apply {
        require(squareX in 0..255 && squareY in 0..255) { "map square outside world: $squareX,$squareY" }
        val baseX = squareX * MAP_SQUARE_SIZE
        val baseY = squareY * MAP_SQUARE_SIZE
        for (plane in 0 until PLANE_COUNT) {
            for (x in baseX until baseX + MAP_SQUARE_SIZE) {
                for (y in baseY until baseY + MAP_SQUARE_SIZE) collision[x, y, plane] = 0
            }
        }
    }

    fun blockFloor(tile: Tile): CollisionMapBuilder = apply {
        collision.add(tile.x, tile.y, tile.plane, CollisionFlag.FLOOR)
    }

    /** Assigns the terrain walk mask while preserving loc and wall flags on the tile. */
    fun setFloorBlocked(tile: Tile, blocked: Boolean): CollisionMapBuilder = apply {
        if (blocked) {
            collision.add(tile.x, tile.y, tile.plane, CollisionFlag.FLOOR)
        } else {
            collision.remove(tile.x, tile.y, tile.plane, CollisionFlag.FLOOR)
        }
    }

    fun blockFloorDecoration(tile: Tile): CollisionMapBuilder = apply {
        collision.add(tile.x, tile.y, tile.plane, CollisionFlag.FLOOR_DECORATION)
    }

    fun blockObject(tile: Tile, width: Int, length: Int): CollisionMapBuilder = apply {
        require(width > 0 && length > 0) { "object dimensions must be positive" }
        for (deltaX in 0 until width) {
            for (deltaY in 0 until length) {
                collision.add(tile.x + deltaX, tile.y + deltaY, tile.plane, CollisionFlag.OBJECT)
            }
        }
    }

    /** Blocks both tiles adjoining a cache wall shape and rotation. */
    fun blockWall(tile: Tile, shape: Int, rotation: Int): CollisionMapBuilder = apply {
        require(shape in WALL_SHAPES) { "unsupported wall shape: $shape" }
        require(rotation in 0..3) { "unsupported wall rotation: $rotation" }
        when (shape) {
            LocShape.WALL_STRAIGHT -> blockStraightWall(tile, rotation)
            LocShape.WALL_DIAGONAL_CORNER, LocShape.WALL_SQUARE_CORNER -> blockDiagonalWall(tile, rotation)
            LocShape.WALL_L -> blockLWall(tile, rotation)
        }
    }

    fun build(): CollisionMap = collision

    private fun blockStraightWall(tile: Tile, rotation: Int) {
        when (rotation) {
            0 -> blockPair(tile, CollisionFlag.WALL_WEST, -1, 0, CollisionFlag.WALL_EAST)
            1 -> blockPair(tile, CollisionFlag.WALL_NORTH, 0, 1, CollisionFlag.WALL_SOUTH)
            2 -> blockPair(tile, CollisionFlag.WALL_EAST, 1, 0, CollisionFlag.WALL_WEST)
            3 -> blockPair(tile, CollisionFlag.WALL_SOUTH, 0, -1, CollisionFlag.WALL_NORTH)
        }
    }

    private fun blockDiagonalWall(tile: Tile, rotation: Int) {
        when (rotation) {
            0 -> blockPair(tile, CollisionFlag.WALL_NORTH_WEST, -1, 1, CollisionFlag.WALL_SOUTH_EAST)
            1 -> blockPair(tile, CollisionFlag.WALL_NORTH_EAST, 1, 1, CollisionFlag.WALL_SOUTH_WEST)
            2 -> blockPair(tile, CollisionFlag.WALL_SOUTH_EAST, 1, -1, CollisionFlag.WALL_NORTH_WEST)
            3 -> blockPair(tile, CollisionFlag.WALL_SOUTH_WEST, -1, -1, CollisionFlag.WALL_NORTH_EAST)
        }
    }

    private fun blockLWall(tile: Tile, rotation: Int) {
        when (rotation) {
            0 -> {
                add(tile, CollisionFlag.WALL_WEST or CollisionFlag.WALL_NORTH)
                add(tile, -1, 0, CollisionFlag.WALL_EAST)
                add(tile, 0, 1, CollisionFlag.WALL_SOUTH)
            }
            1 -> {
                add(tile, CollisionFlag.WALL_NORTH or CollisionFlag.WALL_EAST)
                add(tile, 0, 1, CollisionFlag.WALL_SOUTH)
                add(tile, 1, 0, CollisionFlag.WALL_WEST)
            }
            2 -> {
                add(tile, CollisionFlag.WALL_EAST or CollisionFlag.WALL_SOUTH)
                add(tile, 1, 0, CollisionFlag.WALL_WEST)
                add(tile, 0, -1, CollisionFlag.WALL_NORTH)
            }
            3 -> {
                add(tile, CollisionFlag.WALL_SOUTH or CollisionFlag.WALL_WEST)
                add(tile, 0, -1, CollisionFlag.WALL_NORTH)
                add(tile, -1, 0, CollisionFlag.WALL_EAST)
            }
        }
    }

    private fun blockPair(tile: Tile, flag: Int, deltaX: Int, deltaY: Int, oppositeFlag: Int) {
        add(tile, flag)
        add(tile, deltaX, deltaY, oppositeFlag)
    }

    private fun add(tile: Tile, flag: Int) {
        collision.add(tile.x, tile.y, tile.plane, flag)
    }

    private fun add(tile: Tile, deltaX: Int, deltaY: Int, flag: Int) {
        collision.add(tile.x + deltaX, tile.y + deltaY, tile.plane, flag)
    }

    private companion object {
        const val MAP_SQUARE_SIZE = 64
        const val PLANE_COUNT = 4
        val WALL_SHAPES = setOf(
            LocShape.WALL_STRAIGHT,
            LocShape.WALL_DIAGONAL_CORNER,
            LocShape.WALL_L,
            LocShape.WALL_SQUARE_CORNER,
        )
    }
}
