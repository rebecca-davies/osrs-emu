package emu.server.game.network.input.command

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

class ClientCommandHandlerTest {
    @Test
    fun `stages Jagex console input as one ordered command action`() = runBlocking {
        val actions = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig(capacity = 2, maxPerCycle = 2))
        ClientCommandHandler(actions).handle(ClientCheat("addbots 3"), NoOutput)

        val drained = mutableListOf<PlayerAction>()
        actions.drain(drained::add)

        val action = assertIs<PlayerAction.Command>(drained.single())
        assertEquals("addbots 3", action.input.text)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("not used")
    }
}
