package emu.gateway.login

import emu.game.pathfinding.MovementUpdate
import emu.protocol.osrs239.game.message.PlayerMovement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalPlayerInfoStateTest {
    @Test fun `first cycle publishes cached walk then only changes`() {
        val state = LocalPlayerInfoState()

        assertEquals(1, state.next(MovementUpdate.Idle, runEnabled = false).moveSpeed)
        assertNull(state.next(MovementUpdate.Idle, runEnabled = false).moveSpeed)
        assertEquals(2, state.next(MovementUpdate.Idle, runEnabled = true).moveSpeed)
    }

    @Test fun `two tile run agrees with cached run and needs no temporary override`() {
        val state = LocalPlayerInfoState()
        state.next(MovementUpdate.Idle, runEnabled = true)

        val info = state.next(MovementUpdate.Run(2, 0), runEnabled = true)

        assertEquals(PlayerMovement.Run(2, 0), info.movement)
        assertNull(info.moveSpeed)
        assertNull(info.temporaryMoveSpeed)
    }

    @Test fun `one tile route tail temporarily walks while run remains selected`() {
        val state = LocalPlayerInfoState()
        state.next(MovementUpdate.Idle, runEnabled = true)

        val info = state.next(MovementUpdate.Walk(1, 0), runEnabled = true)

        assertEquals(1, info.temporaryMoveSpeed)
    }

    @Test fun `control run temporarily overrides selected walk`() {
        val state = LocalPlayerInfoState()
        state.next(MovementUpdate.Idle, runEnabled = false)

        val info = state.next(MovementUpdate.Run(2, 0), runEnabled = false)

        assertEquals(2, info.temporaryMoveSpeed)
    }
}
