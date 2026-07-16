package emu.server.game.network.input.chat

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.chat.PublicChatInput
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.handler.HandlerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class MessagePublicHandlerTest {
    @Test fun `IO handler normalizes text but content executes only from the world cycle`() = runBlocking {
        val huffman = HuffmanCodec(ByteArray(256) { 8 })
        val queue = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig())

        MessagePublicHandler(huffman, queue).handle(
            MessagePublic(0, 0, 0, huffman.encode("  hello   world  ")),
            NoOutput,
        )

        val handled = mutableListOf<PlayerAction>()
        queue.drain(handled::add)
        val chat = (handled.single() as PlayerAction.Chat).input as PublicChatInput
        assertEquals("hello world", chat.text)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("chat input must not write from the IO coroutine")
    }
}
