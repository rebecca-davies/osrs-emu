package emu.game.pathfinding

/** Cache loc shape ids whose geometry affects walking collision. */
object LocShape {
    const val WALL_STRAIGHT = 0
    const val WALL_DIAGONAL_CORNER = 1
    const val WALL_L = 2
    const val WALL_SQUARE_CORNER = 3
    const val WALL_DIAGONAL = 9
    const val CENTREPIECE_STRAIGHT = 10
    const val CENTREPIECE_DIAGONAL = 11
    const val ROOF_STRAIGHT = 12
    const val GROUND_DECORATION = 22
}
