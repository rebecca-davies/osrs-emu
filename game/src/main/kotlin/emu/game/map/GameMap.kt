package emu.game.map

import emu.game.loc.Loc
import emu.game.loc.LocRepository
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.canTravel
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.pathfinding.route.BfsPathfinder
import emu.game.pathfinding.route.PathRoute
import emu.game.pathfinding.reach.LocReachStrategy
import emu.game.pathfinding.reach.PathingEntityReachStrategy
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
        val route =
            when (request) {
                is Player.RouteRequest.Location -> {
                    val target = request.target
                    val loc = findLoc(target.type, target.tile)
                    if (loc != target) {
                        PathRoute.Failed
                    } else {
                        pathfinder.findPath(
                            player.movement.position,
                            loc.tile,
                            loc.width,
                            loc.length,
                        ) { x, y ->
                            LocReachStrategy.reached(collision, x, y, loc.tile.plane, loc)
                        }
                    }
                }
                is Player.RouteRequest.Coordinate ->
                    pathfinder.findPath(player.movement.position, request.destination)
                is Player.RouteRequest.PathingEntity -> {
                    pathfinder.findPath(
                        player.movement.position,
                        request.position,
                        request.size,
                        request.size,
                    ) { x, y ->
                        PathingEntityReachStrategy.reached(
                            collision,
                            x,
                            y,
                            player.movement.position.plane,
                            request.position,
                            request.size,
                        )
                    }
                }
            }
        when (request) {
            is Player.RouteRequest.PathingEntity ->
                player.movement.queuePathingEntityRoute(
                    route,
                    request.position,
                    request.size,
                    request.temporaryRun,
                )
            else -> player.movement.queueRoute(route, request.temporaryRun)
        }
        return route
    }

    /** Finds a cache-backed loc placement that has already been prepared off the world thread. */
    fun findLoc(type: Int, tile: Tile): Loc? = locs.find(type, tile)

    /** Whether [loc] is still the authoritative placement at its recorded tile. */
    fun isCurrent(loc: Loc): Boolean = locs.isCurrent(loc)

    /** Whether a size-one entity can operate [loc] from [position] without crossing a wall. */
    fun canReachLoc(position: Tile, loc: Loc): Boolean =
        LocReachStrategy.reached(collision, position.x, position.y, position.plane, loc)

    /** Whether a size-one player can stand beside [target] without crossing a wall. */
    fun canReachEntity(position: Tile, targetPosition: Tile, targetSize: Int): Boolean =
        PathingEntityReachStrategy.reached(
            collision,
            position.x,
            position.y,
            position.plane,
            targetPosition,
            targetSize,
        )

    /** Whether every tile in a square entity footprint is available to NPC placement. */
    fun canOccupy(position: Tile, size: Int): Boolean {
        require(size > 0) { "entity size must be positive" }
        for (x in position.x until position.x + size) {
            for (y in position.y until position.y + size) {
                if (collision.flagsAt(x, y, position.plane) and NPC_PLACEMENT_BLOCKED != 0) return false
            }
        }
        return true
    }

    /** Chooses one collision-valid dumb-pathing step toward a target from a south-west footprint tile. */
    fun nextDumbNpcStep(position: Tile, size: Int, target: Tile): Tile? {
        require(size > 0) { "entity size must be positive" }
        if (position.plane != target.plane) return null
        val northEastX = position.x + size - 1
        val northEastY = position.y + size - 1
        val gapX = when {
            target.x < position.x -> position.x - target.x
            target.x > northEastX -> target.x - northEastX
            else -> 0
        }
        val gapY = when {
            target.y < position.y -> position.y - target.y
            target.y > northEastY -> target.y - northEastY
            else -> 0
        }
        if (maxOf(gapX, gapY) <= 1) return null
        val deltaX = when {
            target.x < position.x -> -1
            target.x > northEastX -> 1
            else -> 0
        }
        val deltaY = when {
            target.y < position.y -> -1
            target.y > northEastY -> 1
            else -> 0
        }
        if (deltaX != 0 && deltaY != 0 && canNpcTravel(position, size, deltaX, deltaY)) {
            return position.translate(deltaX, deltaY)
        }
        if (gapX >= gapY) {
            if (deltaX != 0 && canNpcTravel(position, size, deltaX, 0)) return position.translate(deltaX, 0)
            if (deltaY != 0 && canNpcTravel(position, size, 0, deltaY)) return position.translate(0, deltaY)
        } else {
            if (deltaY != 0 && canNpcTravel(position, size, 0, deltaY)) return position.translate(0, deltaY)
            if (deltaX != 0 && canNpcTravel(position, size, deltaX, 0)) return position.translate(deltaX, 0)
        }
        return null
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

    private fun canNpcTravel(position: Tile, size: Int, deltaX: Int, deltaY: Int): Boolean =
        collision.canTravel(
            position.x,
            position.y,
            position.plane,
            deltaX,
            deltaY,
            size,
            CollisionFlag.BLOCK_NPCS,
        )

    private fun Tile.translate(deltaX: Int, deltaY: Int): Tile =
        Tile(x + deltaX, y + deltaY, plane)

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
        const val NPC_PLACEMENT_BLOCKED =
            CollisionFlag.OBJECT or CollisionFlag.FLOOR_DECORATION or
                CollisionFlag.BLOCK_NPCS or CollisionFlag.FLOOR

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
