package emu.server.game.network.input.ui

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.ui.ButtonClick
import emu.protocol.osrs239.game.message.component.IfButtonX
import emu.server.game.network.input.IncomingPlayerActionQueueOverflow
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.handler.HandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class IfButtonXHandlerTest {
    @Test fun `wire component is converted to a revision neutral queued click without direct output`() = runBlocking {
        val received = mutableListOf<PlayerAction>()
        val handler = IfButtonXHandler(PlayerActionSink { received.add(it) })

        handler.handle(IfButtonX(0x00A0001C, -1, -1, 1), NoOutput)

        val expected: List<PlayerAction> =
            listOf(PlayerAction.Button(ButtonClick(160, 28, -1, -1, 1)))
        assertEquals(expected, received)
    }

    @Test fun `a full action queue terminates the producing connection`() = runBlocking {
        val handler = IfButtonXHandler(PlayerActionSink { false })

        assertFailsWith<IncomingPlayerActionQueueOverflow> {
            handler.handle(IfButtonX(0x00A0001C, -1, -1, 1), NoOutput)
        }
        Unit
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("button input must not write from the IO coroutine")
    }
}
