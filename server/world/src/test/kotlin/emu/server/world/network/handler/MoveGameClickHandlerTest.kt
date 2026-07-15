package emu.server.world.network.handler

import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.transport.pipeline.HandlerContext
import emu.protocol.osrs239.game.message.MoveGameClick
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveGameClickHandlerTest {
    @Test
    fun `handler forwards immutable wire values to the network-world mailbox`() = runBlocking {
        var received: PlayerInput? = null
        val sink = PlayerInputSink { input ->
            received = input
            true
        }
        val handler = MoveGameClickHandler(sink)

        handler.handle(MoveGameClick(3222, 3218, 1), NoOutput)

        assertEquals(PlayerInput.Route(3222, 3218, invertRun = true), received)
    }

    @Test
    fun `handler rejects coordinates outside the world before mailbox admission`() = runBlocking {
        var admissions = 0
        val handler = MoveGameClickHandler(PlayerInputSink { admissions++; true })

        handler.handle(MoveGameClick(0x4000, 3218, 0), NoOutput)

        assertEquals(0, admissions)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: emu.transport.message.OutgoingMessage) =
            error("movement input must not write directly")
    }
}
