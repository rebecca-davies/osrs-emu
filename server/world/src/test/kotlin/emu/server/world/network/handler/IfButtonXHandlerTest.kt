package emu.server.world.network.handler

import emu.game.action.PlayerAction
import emu.game.action.PlayerActionSink
import emu.game.ui.ButtonClick
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.HandlerContext
import emu.protocol.osrs239.game.message.IfButtonX
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

        assertFailsWith<GameInputQueueOverflow> {
            handler.handle(IfButtonX(0x00A0001C, -1, -1, 1), NoOutput)
        }
        Unit
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("button input must not write from the IO coroutine")
    }
}
