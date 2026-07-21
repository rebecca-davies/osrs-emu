package emu.server.game.network.input.client

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.protocol.osrs239.game.message.client.ClientCheat
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.handler.HandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ClientCheatHandlerTest {
    @Test
    fun `stages console input as one ordered player action`() = runBlocking {
        val actions = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig(capacity = 2, maxPerCycle = 2))
        ClientCheatHandler(actions).handle(ClientCheat("addbot 3"), NoOutput)

        val drained = mutableListOf<PlayerAction>()
        actions.drain(drained::add)

        val action = assertIs<PlayerAction.Cheat>(drained.single())
        assertEquals("addbot 3", action.input.text)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("not used")
    }
}
