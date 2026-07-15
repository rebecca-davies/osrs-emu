package emu.server.world.player

import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerMovementProcess
import emu.server.world.map.CollisionMapLoader

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
