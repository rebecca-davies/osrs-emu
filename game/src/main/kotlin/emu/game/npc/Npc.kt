package emu.game.npc

import emu.game.map.Direction
import emu.game.map.MapInstance
import emu.game.map.Tile

/** One world-thread-owned non-player character. */
class Npc internal constructor(
    val index: Int,
    val uid: Long,
    val type: NpcType,
    position: Tile,
    val mapInstance: MapInstance,
    orientation: Direction,
    val targetPlayerId: Long?,
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

    internal var listPosition: Int = -1
    internal var instancePosition: Int = -1

    init {
        require(targetPlayerId == null || targetPlayerId > 0) { "NPC target player id must be positive" }
    }

    /** Moves one adjacent tile and retains its local-list update until cycle cleanup. */
    fun walkTo(destination: Tile) {
        require(destination.plane == position.plane) { "NPC walking cannot change plane" }
        val deltaX = destination.x - position.x
        val deltaY = destination.y - position.y
        val direction = Direction.fromDelta(deltaX, deltaY)
        position = destination
        orientation = direction
        movementUpdate = NpcMovementUpdate.Walk(direction)
    }

    internal fun setPaused(value: Boolean) {
        paused = value
    }

    /** Clears only the movement update published during the completed world cycle. */
    fun finishCycle() {
        movementUpdate = NpcMovementUpdate.Idle
    }
}

/** NPC local-list movement retained for one world cycle. */
sealed interface NpcMovementUpdate {
    data object Idle : NpcMovementUpdate

    data class Walk(val direction: Direction) : NpcMovementUpdate
}
