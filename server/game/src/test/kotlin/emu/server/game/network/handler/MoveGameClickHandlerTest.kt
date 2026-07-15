package emu.server.game.network.handler

import emu.game.pathfinding.PlayerRouteRequestSink
import emu.game.pathfinding.RouteRequestAdmission
import emu.netcore.pipeline.HandlerContext
import emu.protocol.osrs239.game.message.MoveGameClick
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveGameClickHandlerTest {
    @Test
    fun `handler forwards immutable wire values to the network-world mailbox`() = runBlocking {
        var received: Triple<Int, Int, Int>? = null
        val sink = PlayerRouteRequestSink { x, z, key ->
            received = Triple(x, z, key)
            RouteRequestAdmission.QUEUED
        }
        val handler = MoveGameClickHandler(sink)

        handler.handle(MoveGameClick(3222, 3218, 1), NoOutput)

        assertEquals(Triple(3222, 3218, 1), received)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: emu.netcore.message.OutgoingMessage) =
            error("movement input must not write directly")
    }
}
