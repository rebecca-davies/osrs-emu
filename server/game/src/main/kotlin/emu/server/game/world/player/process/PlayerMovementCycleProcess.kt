package emu.server.game.world.player.process

import emu.game.pathfinding.movement.PlayerMovement
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.server.game.world.map.CollisionMapLoader

/** Advances one player and submits non-blocking preparation around the resulting position. */
class PlayerMovementCycleProcess(
    private val movement: PlayerMovementProcess,
    private val collision: CollisionMapLoader,
) {
    fun process(state: PlayerMovement) {
        movement.process(state)
        collision.request(state.position)
    }
}
