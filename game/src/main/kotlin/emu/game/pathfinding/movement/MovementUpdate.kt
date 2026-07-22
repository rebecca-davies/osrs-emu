package emu.game.pathfinding.movement

/** Movement visible to the player-info builder during the current cycle. */
sealed interface MovementUpdate {
    val deltaX: Int
    val deltaY: Int

    data object Idle : MovementUpdate {
        override val deltaX = 0
        override val deltaY = 0
    }

    data class Walk(override val deltaX: Int, override val deltaY: Int) : MovementUpdate

    data class Run(override val deltaX: Int, override val deltaY: Int) : MovementUpdate

    data class Teleport(
        override val deltaX: Int,
        override val deltaY: Int,
        val planeDelta: Int,
    ) : MovementUpdate {
        init {
            require(planeDelta in 0..3) { "teleport plane delta must be in 0..3" }
        }
    }
}
