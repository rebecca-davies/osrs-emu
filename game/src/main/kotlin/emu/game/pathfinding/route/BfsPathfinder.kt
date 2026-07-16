package emu.game.pathfinding.route

import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.canTravel
import kotlin.math.abs

/**
 * Size-one, eight-direction BFS port of Blurite's RuneScape pathfinder.
 *
 * It searches a 128x128 window centred on the source, prevents diagonal corner cutting, falls back
 * to the closest visited tile within ten tiles of an unreachable destination, and compresses the
 * result to at most 25 direction-change waypoints. Supports size-one actors only.
 */
class BfsPathfinder(
    private val collisionMap: CollisionMap,
    private val searchMapSize: Int = DEFAULT_SEARCH_MAP_SIZE,
    private val moveNear: Boolean = true,
    private val maxTurns: Int = DEFAULT_MAX_TURNS,
) {
    private val area = searchMapSize * searchMapSize
    private val directions = IntArray(area)
    private val distances = IntArray(area)
    private val queueX = IntArray(area)
    private val queueY = IntArray(area)

    init {
        require(searchMapSize >= 2 && searchMapSize % 2 == 0) { "search map size must be even and at least two" }
        require(maxTurns > 0) { "max turns must be positive" }
    }

    /** Finds a shortest route from [source] to [destination], or a move-near alternative. */
    fun findPath(source: Tile, destination: Tile): PathRoute {
        if (source.plane != destination.plane) return PathRoute.Failed
        directions.fill(0)
        distances.fill(UNREACHED_DISTANCE)
        val baseX = source.x - searchMapSize / 2
        val baseY = source.y - searchMapSize / 2
        val sourceX = source.x - baseX
        val sourceY = source.y - baseY
        val destinationX = destination.x - baseX
        val destinationY = destination.y - baseY
        fun index(x: Int, y: Int): Int = y * searchMapSize + x

        var reader = 0
        var writer = 0
        directions[index(sourceX, sourceY)] = SOURCE_DIRECTION
        distances[index(sourceX, sourceY)] = 0
        queueX[writer] = sourceX
        queueY[writer++] = sourceY
        var currentX = sourceX
        var currentY = sourceY
        var pathFound = false

        fun visit(fromX: Int, fromY: Int, x: Int, y: Int, backDirection: Int) {
            val nextIndex = index(x, y)
            if (directions[nextIndex] != 0) return
            directions[nextIndex] = backDirection
            distances[nextIndex] = distances[index(fromX, fromY)] + 1
            queueX[writer] = x
            queueY[writer++] = y
        }

        while (reader < writer) {
            currentX = queueX[reader]
            currentY = queueY[reader++]
            if (currentX == destinationX && currentY == destinationY) {
                pathFound = true
                break
            }
            val worldX = currentX + baseX
            val worldY = currentY + baseY
            if (currentX > 0 && collisionMap.canTravel(worldX, worldY, source.plane, -1, 0)) {
                visit(currentX, currentY, currentX - 1, currentY, EAST)
            }
            if (currentX < searchMapSize - 1 && collisionMap.canTravel(worldX, worldY, source.plane, 1, 0)) {
                visit(currentX, currentY, currentX + 1, currentY, WEST)
            }
            if (currentY > 0 && collisionMap.canTravel(worldX, worldY, source.plane, 0, -1)) {
                visit(currentX, currentY, currentX, currentY - 1, NORTH)
            }
            if (currentY < searchMapSize - 1 && collisionMap.canTravel(worldX, worldY, source.plane, 0, 1)) {
                visit(currentX, currentY, currentX, currentY + 1, SOUTH)
            }
            if (currentX > 0 && currentY > 0 && collisionMap.canTravel(worldX, worldY, source.plane, -1, -1)) {
                visit(currentX, currentY, currentX - 1, currentY - 1, NORTH_EAST)
            }
            if (
                currentX < searchMapSize - 1 &&
                currentY > 0 &&
                collisionMap.canTravel(worldX, worldY, source.plane, 1, -1)
            ) {
                visit(currentX, currentY, currentX + 1, currentY - 1, NORTH_WEST)
            }
            if (
                currentX > 0 &&
                currentY < searchMapSize - 1 &&
                collisionMap.canTravel(worldX, worldY, source.plane, -1, 1)
            ) {
                visit(currentX, currentY, currentX - 1, currentY + 1, SOUTH_EAST)
            }
            if (
                currentX < searchMapSize - 1 &&
                currentY < searchMapSize - 1 &&
                collisionMap.canTravel(worldX, worldY, source.plane, 1, 1)
            ) {
                visit(currentX, currentY, currentX + 1, currentY + 1, SOUTH_WEST)
            }
        }

        if (!pathFound) {
            if (!moveNear) return PathRoute.Failed
            val alternative = closestApproach(destinationX, destinationY, distances) ?: return PathRoute.Failed
            currentX = alternative.first
            currentY = alternative.second
        }

        val waypoints = ArrayDeque<Tile>(maxTurns)
        var nextDirection = directions[index(currentX, currentY)]
        var currentDirection = -1
        repeat(area) {
            if (currentX == sourceX && currentY == sourceY) {
                return PathRoute(waypoints.toList(), alternative = !pathFound, success = true)
            }
            if (currentDirection != nextDirection) {
                if (waypoints.size >= maxTurns) waypoints.removeLast()
                waypoints.addFirst(Tile(currentX + baseX, currentY + baseY, source.plane))
                currentDirection = nextDirection
            }
            if (currentDirection and EAST != 0) currentX++ else if (currentDirection and WEST != 0) currentX--
            if (currentDirection and NORTH != 0) currentY++ else if (currentDirection and SOUTH != 0) currentY--
            nextDirection = directions[index(currentX, currentY)]
        }
        return PathRoute.Failed
    }

    private fun closestApproach(destinationX: Int, destinationY: Int, distances: IntArray): Pair<Int, Int>? {
        var lowestCost = MAX_ALTERNATIVE_COST
        var shortestPath = MAX_ALTERNATIVE_DISTANCE
        var result: Pair<Int, Int>? = null
        val valid = 0 until searchMapSize
        for (x in destinationX - ALTERNATIVE_RADIUS..destinationX + ALTERNATIVE_RADIUS) {
            for (y in destinationY - ALTERNATIVE_RADIUS..destinationY + ALTERNATIVE_RADIUS) {
                if (x !in valid || y !in valid) continue
                val distance = distances[y * searchMapSize + x]
                if (distance >= MAX_ALTERNATIVE_DISTANCE) continue
                val dx = abs(x - destinationX)
                val dy = abs(y - destinationY)
                val cost = dx * dx + dy * dy
                if (cost < lowestCost || cost == lowestCost && distance < shortestPath) {
                    lowestCost = cost
                    shortestPath = distance
                    result = x to y
                }
            }
        }
        return result
    }

    private companion object {
        const val DEFAULT_SEARCH_MAP_SIZE = 128
        const val DEFAULT_MAX_TURNS = 25
        const val UNREACHED_DISTANCE = 999
        const val SOURCE_DIRECTION = 99
        const val MAX_ALTERNATIVE_COST = 1000
        const val MAX_ALTERNATIVE_DISTANCE = 100
        const val ALTERNATIVE_RADIUS = 10

        const val NORTH = 0x1
        const val EAST = 0x2
        const val SOUTH = 0x4
        const val WEST = 0x8
        const val SOUTH_WEST = WEST or SOUTH
        const val NORTH_WEST = WEST or NORTH
        const val SOUTH_EAST = EAST or SOUTH
        const val NORTH_EAST = EAST or NORTH
    }
}
