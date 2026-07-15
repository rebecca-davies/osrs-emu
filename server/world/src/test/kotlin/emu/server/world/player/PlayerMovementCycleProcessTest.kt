package emu.server.world.player

import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.pathfinding.Tile
import emu.server.world.map.CollisionMapLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerMovementCycleProcessTest {
    @Test
    fun `movement submits collision preparation without loading on the world cycle`() {
        val requested = mutableListOf<Tile>()
        val loader =
            object : CollisionMapLoader {
                override fun prepare(position: Tile) = error("world cycle must not block on collision preparation")

                override fun request(position: Tile): Boolean {
                    requested += position
                    return true
                }
            }
        val movement = PlayerMovement(Tile(3_200, 3_200))
        val movementProcess = PlayerMovementProcess(OpenCollisionMap)
        movementProcess.routeTo(movement, Tile(3_201, 3_200))
        val process = PlayerMovementCycleProcess(movementProcess, loader)

        process.process(movement)

        assertEquals(Tile(3_201, 3_200), movement.position)
        assertEquals(listOf(movement.position), requested)
    }
}
