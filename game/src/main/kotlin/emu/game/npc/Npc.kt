package emu.game.npc

import emu.game.map.Direction
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.player.Player

/** One world-thread-owned non-player character. */
class Npc internal constructor(
    val index: Int,
    val uid: Long,
    val type: NpcType,
    position: Tile,
    val mapInstance: MapInstance,
    orientation: Direction,
    target: Player?,
    paused: Boolean,
) {
    var position: Tile = position
        private set

    var orientation: Direction = orientation
        private set

    var movementUpdate: NpcMovementUpdate = NpcMovementUpdate.Idle
        private set

    var paused: Boolean = paused
        private set

    var target: Player? = target
        private set

    internal var listPosition: Int = -1
    internal var instancePosition: Int = -1

    /** Moves one adjacent tile and retains its local-list update until cycle cleanup. */
    fun walkTo(destination: Tile) {
        require(destination.plane == position.plane) { "NPC walking cannot change plane" }
        val deltaX = destination.x - position.x
        val deltaY = destination.y - position.y
        val direction = Direction.fromDelta(deltaX, deltaY)
        position = destination
        orientation = direction
        movementUpdate = NpcMovementUpdate.Walk.from(direction)
    }

    internal fun setPaused(value: Boolean) {
        paused = value
    }

    /** Releases the live player reference currently hunted by this NPC. */
    fun clearTarget() {
        target = null
    }

    /** Clears only the movement update published during the completed world cycle. */
    fun finishCycle() {
        movementUpdate = NpcMovementUpdate.Idle
    }
}

/** NPC local-list movement retained for one world cycle. */
sealed interface NpcMovementUpdate {
    data object Idle : NpcMovementUpdate

    enum class Walk(val direction: Direction) : NpcMovementUpdate {
        SOUTH_WEST(Direction.SOUTH_WEST),
        SOUTH(Direction.SOUTH),
        SOUTH_EAST(Direction.SOUTH_EAST),
        WEST(Direction.WEST),
        EAST(Direction.EAST),
        NORTH_WEST(Direction.NORTH_WEST),
        NORTH(Direction.NORTH),
        NORTH_EAST(Direction.NORTH_EAST),

        ;

        companion object {
            fun from(direction: Direction): Walk =
                when (direction) {
                    Direction.SOUTH_WEST -> SOUTH_WEST
                    Direction.SOUTH -> SOUTH
                    Direction.SOUTH_EAST -> SOUTH_EAST
                    Direction.WEST -> WEST
                    Direction.EAST -> EAST
                    Direction.NORTH_WEST -> NORTH_WEST
                    Direction.NORTH -> NORTH
                    Direction.NORTH_EAST -> NORTH_EAST
                }
        }
    }
}
