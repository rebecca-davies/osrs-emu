package emu.game.map

/** One of the eight tile directions used by pathing entities. */
enum class Direction {
    SOUTH_WEST,
    SOUTH,
    SOUTH_EAST,
    WEST,
    EAST,
    NORTH_WEST,
    NORTH,
    NORTH_EAST,

    ;

    companion object {
        /** Resolves one adjacent tile delta into its eight-way direction. */
        fun fromDelta(deltaX: Int, deltaY: Int): Direction {
            require(deltaX in -1..1 && deltaY in -1..1 && (deltaX != 0 || deltaY != 0)) {
                "direction delta must be one adjacent tile"
            }
            return when ((deltaY + 1) * 3 + deltaX + 1) {
                0 -> SOUTH_WEST
                1 -> SOUTH
                2 -> SOUTH_EAST
                3 -> WEST
                5 -> EAST
                6 -> NORTH_WEST
                7 -> NORTH
                8 -> NORTH_EAST
                else -> error("unreachable direction delta")
            }
        }
    }
}
