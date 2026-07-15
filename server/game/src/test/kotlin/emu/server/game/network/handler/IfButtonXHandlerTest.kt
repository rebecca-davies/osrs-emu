package emu.server.game.network.handler

import emu.game.ui.ButtonClick
import emu.game.ui.PlayerButtonSink
import emu.netcore.message.OutgoingMessage
import emu.netcore.pipeline.HandlerContext
import emu.protocol.osrs239.game.message.IfButtonX
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class IfButtonXHandlerTest {
    @Test fun `wire component is converted to a revision neutral queued click without direct output`() = runBlocking {
        val received = mutableListOf<ButtonClick>()
        val handler = IfButtonXHandler(PlayerButtonSink { received.add(it) })

        handler.handle(IfButtonX(0x00A0001C, -1, -1, 1), NoOutput)

        assertEquals(listOf(ButtonClick(160, 28, -1, -1, 1)), received)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("button input must not write from the IO coroutine")
    }
}
