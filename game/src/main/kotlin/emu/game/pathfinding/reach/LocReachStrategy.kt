package emu.game.pathfinding.reach

import emu.game.loc.Loc
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.LocShape

/** Revision-neutral size-one reach rules for cache-shaped RuneScape locs. */
internal object LocReachStrategy {
    fun reached(
        collision: CollisionMap,
        sourceX: Int,
        sourceY: Int,
        plane: Int,
        target: Loc,
    ): Boolean {
        if (plane != target.tile.plane) return false
        if (sourceX == target.tile.x && sourceY == target.tile.y) return true
        return when (target.shape) {
            in LocShape.WALL_STRAIGHT..LocShape.WALL_SQUARE_CORNER,
            LocShape.WALL_DIAGONAL,
            -> reachWall(collision, sourceX, sourceY, plane, target)

            in LocShape.WALL_DECOR_STRAIGHT_NO_OFFSET..LocShape.WALL_DECOR_DIAGONAL_BOTH ->
                reachWallDecoration(collision, sourceX, sourceY, plane, target)

            LocShape.CENTREPIECE_STRAIGHT,
            LocShape.CENTREPIECE_DIAGONAL,
            LocShape.GROUND_DECORATION,
            -> reachRectangle(collision, sourceX, sourceY, plane, target)

            else -> false
        }
    }

    private fun reachRectangle(
        collision: CollisionMap,
        sourceX: Int,
        sourceY: Int,
        plane: Int,
        target: Loc,
    ): Boolean {
        val west = target.tile.x
        val east = west + target.width - 1
        val south = target.tile.y
        val north = south + target.length - 1
        if (sourceX in west..east && sourceY in south..north) return true
        val flags = collision.flagsAt(sourceX, sourceY, plane)
        return when {
            sourceX == west - 1 && sourceY in south..north ->
                flags and CollisionFlag.WALL_EAST == 0 &&
                    target.forceApproachFlags and ForceApproach.WEST == 0
            sourceX == east + 1 && sourceY in south..north ->
                flags and CollisionFlag.WALL_WEST == 0 &&
                    target.forceApproachFlags and ForceApproach.EAST == 0
            sourceY == south - 1 && sourceX in west..east ->
                flags and CollisionFlag.WALL_NORTH == 0 &&
                    target.forceApproachFlags and ForceApproach.SOUTH == 0
            sourceY == north + 1 && sourceX in west..east ->
                flags and CollisionFlag.WALL_SOUTH == 0 &&
                    target.forceApproachFlags and ForceApproach.NORTH == 0
            else -> false
        }
    }

    private fun reachWall(
        collision: CollisionMap,
        sourceX: Int,
        sourceY: Int,
        plane: Int,
        target: Loc,
    ): Boolean {
        val targetX = target.tile.x
        val targetY = target.tile.y
        val flags = collision.flagsAt(sourceX, sourceY, plane)
        fun clear(mask: Int): Boolean = flags and mask == 0
        return when (target.shape) {
            LocShape.WALL_STRAIGHT ->
                when (target.angle) {
                    0 ->
                        sourceX == targetX - 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.BLOCK_NORTH) ||
                            sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.BLOCK_SOUTH)
                    1 ->
                        sourceX == targetX && sourceY == targetY + 1 ||
                            sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_WEST) ||
                            sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_EAST)
                    2 ->
                        sourceX == targetX + 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.BLOCK_NORTH) ||
                            sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.BLOCK_SOUTH)
                    else ->
                        sourceX == targetX && sourceY == targetY - 1 ||
                            sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_WEST) ||
                            sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_EAST)
                }

            LocShape.WALL_L ->
                when (target.angle) {
                    0 ->
                        sourceX == targetX - 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY + 1 ||
                            sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_EAST) ||
                            sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.BLOCK_SOUTH)
                    1 ->
                        sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_WEST) ||
                            sourceX == targetX && sourceY == targetY + 1 ||
                            sourceX == targetX + 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.BLOCK_SOUTH)
                    2 ->
                        sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_WEST) ||
                            sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.BLOCK_NORTH) ||
                            sourceX == targetX + 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY - 1
                    else ->
                        sourceX == targetX - 1 && sourceY == targetY ||
                            sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.BLOCK_NORTH) ||
                            sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.BLOCK_EAST) ||
                            sourceX == targetX && sourceY == targetY - 1
                }

            LocShape.WALL_DIAGONAL ->
                sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.WALL_SOUTH) ||
                    sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.WALL_NORTH) ||
                    sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.WALL_EAST) ||
                    sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.WALL_WEST)

            else -> false
        }
    }

    private fun reachWallDecoration(
        collision: CollisionMap,
        sourceX: Int,
        sourceY: Int,
        plane: Int,
        target: Loc,
    ): Boolean {
        val targetX = target.tile.x
        val targetY = target.tile.y
        val flags = collision.flagsAt(sourceX, sourceY, plane)
        fun clear(mask: Int): Boolean = flags and mask == 0
        if (target.shape == LocShape.WALL_DECOR_DIAGONAL_BOTH) {
            return sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.WALL_SOUTH) ||
                sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.WALL_NORTH) ||
                sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.WALL_EAST) ||
                sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.WALL_WEST)
        }
        if (target.shape !in LocShape.WALL_DECOR_DIAGONAL_OFFSET..LocShape.WALL_DECOR_DIAGONAL_NO_OFFSET) {
            return false
        }
        val angle =
            if (target.shape == LocShape.WALL_DECOR_DIAGONAL_NO_OFFSET) {
                (target.angle + 2) and 3
            } else {
                target.angle
            }
        return when (angle) {
            0 ->
                sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.WALL_WEST) ||
                    sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.WALL_NORTH)
            1 ->
                sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.WALL_EAST) ||
                    sourceX == targetX && sourceY == targetY - 1 && clear(CollisionFlag.WALL_NORTH)
            2 ->
                sourceX == targetX - 1 && sourceY == targetY && clear(CollisionFlag.WALL_EAST) ||
                    sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.WALL_SOUTH)
            else ->
                sourceX == targetX + 1 && sourceY == targetY && clear(CollisionFlag.WALL_WEST) ||
                    sourceX == targetX && sourceY == targetY + 1 && clear(CollisionFlag.WALL_SOUTH)
        }
    }

    private object ForceApproach {
        const val NORTH = 0x1
        const val EAST = 0x2
        const val SOUTH = 0x4
        const val WEST = 0x8
    }
}
