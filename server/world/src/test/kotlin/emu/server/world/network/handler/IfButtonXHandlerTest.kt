package emu.server.world.network.handler

import emu.game.input.PlayerInput
import emu.game.input.PlayerInputSink
import emu.game.ui.ButtonClick
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.HandlerContext
import emu.protocol.osrs239.game.message.IfButtonX
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class IfButtonXHandlerTest {
    @Test fun `wire component is converted to a revision neutral queued click without direct output`() = runBlocking {
        val received = mutableListOf<PlayerInput>()
        val handler = IfButtonXHandler(PlayerInputSink { received.add(it) })

        handler.handle(IfButtonX(0x00A0001C, -1, -1, 1), NoOutput)

        val expected: List<PlayerInput> =
            listOf(PlayerInput.Button(ButtonClick(160, 28, -1, -1, 1)))
        assertEquals(expected, received)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("button input must not write from the IO coroutine")
    }
}
