package emu.server.game.network.input

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.client.Idle
import emu.protocol.osrs239.game.message.component.CloseModal
import emu.server.game.network.input.client.IdleHandler
import emu.server.game.network.input.ui.CloseModalHandler
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.handler.HandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class PlayerControlHandlerTest {
    @Test
    fun `modal close and idle become revision neutral actions without direct output`() = runBlocking {
        val received = mutableListOf<PlayerAction>()
        val actions = PlayerActionSink(received::add)

        CloseModalHandler(actions).handle(CloseModal, NoOutput)
        IdleHandler(actions).handle(Idle, NoOutput)

        assertEquals(listOf(PlayerAction.CloseModal, PlayerAction.IdleLogout), received)
    }

    @Test
    fun `a full action queue terminates either producing packet path`() = runBlocking {
        val full = PlayerActionSink { false }

        assertFailsWith<IncomingPlayerActionQueueOverflow> {
            CloseModalHandler(full).handle(CloseModal, NoOutput)
        }
        assertFailsWith<IncomingPlayerActionQueueOverflow> {
            IdleHandler(full).handle(Idle, NoOutput)
        }
        Unit
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) =
            error("player control input must not write from the IO coroutine")
    }
}
