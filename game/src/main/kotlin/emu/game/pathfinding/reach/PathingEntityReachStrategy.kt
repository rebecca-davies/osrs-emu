package emu.game.pathfinding.reach

import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap

/** Size-one cardinal reach against the exclusive boundary of a square pathing entity. */
internal object PathingEntityReachStrategy {
    fun reached(
        collision: CollisionMap,
        sourceX: Int,
        sourceY: Int,
        plane: Int,
        targetPosition: Tile,
        targetSize: Int,
    ): Boolean {
        require(targetSize in 1..0xFF) { "pathing target size must fit an unsigned byte" }
        if (plane != targetPosition.plane) return false
        val west = targetPosition.x
        val east = west + targetSize - 1
        val south = targetPosition.y
        val north = south + targetSize - 1
        if (sourceX in west..east && sourceY in south..north) return false
        val flags = collision.flagsAt(sourceX, sourceY, plane)
        return when {
            sourceX == west - 1 && sourceY in south..north ->
                flags and CollisionFlag.WALL_EAST == 0
            sourceX == east + 1 && sourceY in south..north ->
                flags and CollisionFlag.WALL_WEST == 0
            sourceY == south - 1 && sourceX in west..east ->
                flags and CollisionFlag.WALL_NORTH == 0
            sourceY == north + 1 && sourceX in west..east ->
                flags and CollisionFlag.WALL_SOUTH == 0
            else -> false
        }
    }
}
