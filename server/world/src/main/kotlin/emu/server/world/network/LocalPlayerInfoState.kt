package emu.server.world.network

import emu.game.pathfinding.MovementUpdate
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerMovement

/** Tracks the movement speed cached by the rev-239 client between player-info packets. */
internal class LocalPlayerInfoState {
    private var knownMoveSpeed = STATIONARY_SPEED

    fun next(update: MovementUpdate, runEnabled: Boolean): PlayerInfo {
        val selectedSpeed = if (runEnabled) RUN_SPEED else WALK_SPEED
        val moveSpeed = selectedSpeed.takeIf { it != knownMoveSpeed }
        knownMoveSpeed = selectedSpeed

        val actualSpeed =
            when (update) {
                MovementUpdate.Idle -> selectedSpeed
                is MovementUpdate.Walk -> WALK_SPEED
                is MovementUpdate.Run -> RUN_SPEED
            }
        val temporaryMoveSpeed = actualSpeed.takeIf { update != MovementUpdate.Idle && it != selectedSpeed }

        return PlayerInfo(
            movement = update.toProtocolMovement(),
            moveSpeed = moveSpeed,
            temporaryMoveSpeed = temporaryMoveSpeed,
        )
    }

    private companion object {
        const val WALK_SPEED = 1
        const val RUN_SPEED = 2
        const val STATIONARY_SPEED = 127
    }
}

/** Keeps protocol-specific direction encoding outside the game simulation module. */
private fun MovementUpdate.toProtocolMovement(): PlayerMovement? =
    when (this) {
        MovementUpdate.Idle -> null
        is MovementUpdate.Walk -> PlayerMovement.Walk(deltaX, deltaY)
        is MovementUpdate.Run -> PlayerMovement.Run(deltaX, deltaY)
    }
