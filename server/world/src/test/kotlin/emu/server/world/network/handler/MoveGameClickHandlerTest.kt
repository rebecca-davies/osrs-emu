package emu.server.world.network.handler

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.message.MoveGameClick
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.HandlerContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoveGameClickHandlerTest {
    @Test
    fun `handler forwards immutable wire values to the game input queue`() = runBlocking {
        var received: PlayerAction? = null
        val sink = PlayerActionSink { action ->
            received = action
            true
        }
        val handler = MoveGameClickHandler(sink)

        handler.handle(MoveGameClick(3222, 3218, 1), NoOutput)

        assertEquals(PlayerAction.Route(3222, 3218, invertRun = true), received)
    }

    @Test
    fun `handler terminates a connection that sends coordinates outside the world`() = runBlocking {
        var queued = 0
        val handler = MoveGameClickHandler(PlayerActionSink { queued++; true })

        assertFailsWith<IllegalArgumentException> {
            handler.handle(MoveGameClick(0x4000, 3218, 0), NoOutput)
        }

        assertEquals(0, queued)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) =
            error("movement input must not write directly")
    }
}
