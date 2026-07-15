package emu.server.world.network.handler

import emu.compression.HuffmanCodec
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputQueue
import emu.game.input.PlayerInputQueueConfig
import kotlinx.coroutines.runBlocking
import emu.transport.pipeline.HandlerContext
import emu.transport.message.OutgoingMessage
import emu.protocol.osrs239.game.message.MessagePublic
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagePublicHandlerTest {
    @Test fun `IO handler normalizes text but content executes only from the cycle mailbox`() = runBlocking {
        val huffman = HuffmanCodec(ByteArray(256) { 8 })
        val queue = PlayerInputQueue(PlayerInputQueueConfig())

        MessagePublicHandler(huffman, queue).handle(
            MessagePublic(0, 0, 0, huffman.encode("  hello   world  ")),
            NoOutput,
        )

        val handled = mutableListOf<PlayerInput>()
        queue.drain(handled::add)
        val chat = (handled.single() as PlayerInput.Chat).input as emu.game.chat.PublicChatInput
        assertEquals("hello world", chat.text)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("chat input must not write from the IO coroutine")
    }
}
