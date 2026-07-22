package emu.game.map

import emu.game.loc.Loc
import emu.game.loc.LocRepository
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.pathfinding.route.BfsPathfinder
import emu.game.pathfinding.route.PathRoute
import emu.game.player.Player

/** World-owned loc lookup, collision, route search, and movement advancement. */
class GameMap(
    private val collision: CollisionMap,
    private val locs: LocRepository = LocRepository.EMPTY,
    private val requestAreas: (Tile) -> Boolean = { true },
) {
    private val pathfinder = BfsPathfinder(collision)
    private val pendingAreaRequests = IntArray(MAP_SQUARE_COUNT)
    private val pendingMapSquares = BooleanArray(MAP_SQUARE_COUNT)
    private var pendingAreaRequestHead = 0
    private var pendingAreaRequestTail = 0
    private var pendingAreaRequestCount = 0

    /** Resolves and consumes the player's latest coalesced route request, when present. */
    fun resolveRoute(player: Player): PathRoute? {
        val request = player.takeRouteRequest() ?: return null
        val route = pathfinder.findPath(player.movement.position, request.destination)
        player.movement.queueRoute(route, request.temporaryRun)
        return route
    }

    /** Finds a cache-backed loc placement that has already been prepared off the world thread. */
    fun findLoc(type: Int, tile: Tile): Loc? = locs.find(type, tile)

    /** Whether a size-one entity can operate [loc] from [position] without crossing a wall. */
    fun canReachLoc(position: Tile, loc: Loc): Boolean {
        if (!loc.isAdjacentTo(position)) return false
        val flags = collision.flagsAt(position.x, position.y, position.plane)
        return when {
            position.x < loc.tile.x -> flags and CollisionFlag.WALL_EAST == 0
            position.x >= loc.tile.x + loc.width -> flags and CollisionFlag.WALL_WEST == 0
            position.y < loc.tile.y -> flags and CollisionFlag.WALL_NORTH == 0
            else -> flags and CollisionFlag.WALL_SOUTH == 0
        }
    }

    /** Retries a bounded share of previously rejected map-area preparation requests. */
    fun retryAreaRequests() {
        repeat(minOf(pendingAreaRequestCount, MAX_AREA_REQUEST_RETRIES_PER_CYCLE)) {
            val packedTile = removeAreaRequest()
            val tile = unpackTile(packedTile)
            var accepted = false
            try {
                accepted = requestAreas(tile)
            } finally {
                if (accepted) {
                    pendingMapSquares[mapSquareKey(tile)] = false
                } else {
                    addAreaRequest(packedTile)
                }
            }
        }
    }

    /** Requests non-blocking preparation around a player's current or destination tile. */
    fun prepareArea(tile: Tile) {
        val mapSquare = mapSquareKey(tile)
        if (pendingMapSquares[mapSquare] || requestAreas(tile)) return
        pendingMapSquares[mapSquare] = true
        addAreaRequest(packTile(tile))
    }

    /** Resolves script-created walking, advances movement, and prepares a newly entered map square. */
    fun advance(player: Player) {
        resolveRoute(player)
        if (player.movement.update is MovementUpdate.Teleport) {
            prepareArea(player.movement.position)
            return
        }
        val previous = player.movement.position
        player.movement.advance(collision)
        val current = player.movement.position
        if (previous.x shr MAP_SQUARE_SHIFT != current.x shr MAP_SQUARE_SHIFT ||
            previous.y shr MAP_SQUARE_SHIFT != current.y shr MAP_SQUARE_SHIFT
        ) {
            prepareArea(current)
        }
    }

    private fun addAreaRequest(packedTile: Int) {
        check(pendingAreaRequestCount < pendingAreaRequests.size) {
            "every RuneScape map square already has a pending collision request"
        }
        pendingAreaRequests[pendingAreaRequestTail] = packedTile
        pendingAreaRequestTail = (pendingAreaRequestTail + 1) and MAP_SQUARE_KEY_MASK
        pendingAreaRequestCount++
    }

    private fun removeAreaRequest(): Int {
        check(pendingAreaRequestCount > 0) { "no collision area request is pending" }
        val packedTile = pendingAreaRequests[pendingAreaRequestHead]
        pendingAreaRequestHead = (pendingAreaRequestHead + 1) and MAP_SQUARE_KEY_MASK
        pendingAreaRequestCount--
        return packedTile
    }

    private companion object {
        const val MAP_SQUARE_SHIFT = 6
        const val MAP_SQUARE_COORDINATE_BITS = 8
        const val MAP_SQUARE_COUNT = 1 shl (MAP_SQUARE_COORDINATE_BITS * 2)
        const val MAP_SQUARE_KEY_MASK = MAP_SQUARE_COUNT - 1
        const val WORLD_COORDINATE_BITS = 14
        const val WORLD_COORDINATE_MASK = (1 shl WORLD_COORDINATE_BITS) - 1
        const val PLANE_SHIFT = WORLD_COORDINATE_BITS * 2
        const val MAX_AREA_REQUEST_RETRIES_PER_CYCLE = 64

        fun mapSquareKey(tile: Tile): Int =
            (tile.x shr MAP_SQUARE_SHIFT) shl MAP_SQUARE_COORDINATE_BITS or
                (tile.y shr MAP_SQUARE_SHIFT)

        fun packTile(tile: Tile): Int =
            (tile.plane shl PLANE_SHIFT) or (tile.x shl WORLD_COORDINATE_BITS) or tile.y

        fun unpackTile(packed: Int): Tile =
            Tile(
                x = packed ushr WORLD_COORDINATE_BITS and WORLD_COORDINATE_MASK,
                y = packed and WORLD_COORDINATE_MASK,
                plane = packed ushr PLANE_SHIFT,
            )
    }
}
