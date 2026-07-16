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
}
