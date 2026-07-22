package emu.game.pathfinding.collision

/**
 * RuneScape collision bits and movement masks used by Blurite's pathfinder.
 *
 * Derived from `blurite/pathfinder` at commit b5a53b2 under the ISC license. The full notice is
 * shipped in `META-INF/LICENSE-blurite-pathfinder.txt`.
 */
object CollisionFlag {
    const val WALL_NORTH_WEST = 0x1
    const val WALL_NORTH = 0x2
    const val WALL_NORTH_EAST = 0x4
    const val WALL_EAST = 0x8
    const val WALL_SOUTH_EAST = 0x10
    const val WALL_SOUTH = 0x20
    const val WALL_SOUTH_WEST = 0x40
    const val WALL_WEST = 0x80
    const val OBJECT = 0x100
    const val FLOOR_DECORATION = 0x40000
    const val BLOCK_NPCS = 0x80000
    const val BLOCK_PLAYERS = 0x100000
    const val FLOOR = 0x200000

    private const val FLOOR_BLOCKED = FLOOR or FLOOR_DECORATION

    const val BLOCK_WEST = WALL_EAST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_EAST = WALL_WEST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_SOUTH = WALL_NORTH or OBJECT or FLOOR_BLOCKED
    const val BLOCK_NORTH = WALL_SOUTH or OBJECT or FLOOR_BLOCKED
    const val BLOCK_SOUTH_WEST = WALL_NORTH or WALL_NORTH_EAST or WALL_EAST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_SOUTH_EAST = WALL_NORTH_WEST or WALL_NORTH or WALL_WEST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_NORTH_WEST = WALL_EAST or WALL_SOUTH_EAST or WALL_SOUTH or OBJECT or FLOOR_BLOCKED
    const val BLOCK_NORTH_EAST = WALL_SOUTH or WALL_SOUTH_WEST or WALL_WEST or OBJECT or FLOOR_BLOCKED

    const val BLOCK_NORTH_AND_SOUTH_EAST =
        WALL_NORTH or WALL_NORTH_EAST or WALL_EAST or WALL_SOUTH_EAST or WALL_SOUTH or OBJECT or FLOOR_BLOCKED
    const val BLOCK_NORTH_AND_SOUTH_WEST =
        WALL_NORTH_WEST or WALL_NORTH or WALL_SOUTH or WALL_SOUTH_WEST or WALL_WEST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_NORTH_EAST_AND_WEST =
        WALL_NORTH_WEST or WALL_NORTH or WALL_NORTH_EAST or WALL_EAST or WALL_WEST or OBJECT or FLOOR_BLOCKED
    const val BLOCK_SOUTH_EAST_AND_WEST =
        WALL_EAST or WALL_SOUTH_EAST or WALL_SOUTH or WALL_SOUTH_WEST or WALL_WEST or OBJECT or FLOOR_BLOCKED
}
