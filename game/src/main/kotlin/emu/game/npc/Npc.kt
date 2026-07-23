package emu.game.npc

import emu.game.entity.EntityHealthBar
import emu.game.entity.EntityHitmark
import emu.game.entity.EntityInfoSnapshot
import emu.game.entity.EntityInfoUpdates
import emu.game.entity.EntitySpotAnimation
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
    private val infoUpdates = EntityInfoUpdates()

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

    var animationUpdate: NpcAnimation? = null
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

    /** Requests an animation for this cycle; `-1` stops the current client animation. */
    fun playAnimation(id: Int, delay: Int = 0) {
        animationUpdate = NpcAnimation(id, delay)
    }

    /** Shows one bounded hitmark in this cycle's NPC information update. */
    fun showHitmark(damage: Int, delay: Int = 0): Boolean =
        infoUpdates.showHitmark(EntityHitmark(damage, delay))

    /** Replaces this cycle's health-bar update with the latest authoritative value. */
    fun showHealthBar(current: Int, maximum: Int, delay: Int = 0) {
        infoUpdates.showHealthBar(EntityHealthBar(current, maximum, delay))
    }

    /** Sets one bounded spot-animation slot for this cycle's NPC information update. */
    fun playSpotAnimation(id: Int, delay: Int = 0, height: Int = 0, slot: Int = 0): Boolean =
        infoUpdates.playSpotAnimation(EntitySpotAnimation(id, delay, height, slot))

    /** Immutable information visuals retained for every observer during this cycle. */
    fun infoSnapshot(): EntityInfoSnapshot? = infoUpdates.snapshot()

    /** Clears movement, animation, hitmark, health-bar, and spot-animation updates after output. */
    fun finishCycle() {
        movementUpdate = NpcMovementUpdate.Idle
        infoUpdates.finishCycle()
        animationUpdate = null
    }
}

/** One NPC sequence request retained until cycle cleanup. */
data class NpcAnimation(val id: Int, val delay: Int = 0) {
    init {
        require(id == -1 || id in 0 until 0xFFFF) {
            "NPC animation id must fit below the unsigned-short null sentinel or be -1"
        }
        require(delay in 0..0xFF) { "NPC animation delay must fit an unsigned byte" }
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
