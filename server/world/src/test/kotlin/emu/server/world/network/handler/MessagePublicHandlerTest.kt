package emu.server.world.network.handler

import emu.compression.HuffmanCodec
import emu.game.chat.ChatActionRegistry
import emu.game.chat.ChatInput
import emu.game.chat.PlayerChatQueue
import emu.game.chat.chatActions
import kotlinx.coroutines.runBlocking
import emu.netcore.pipeline.HandlerContext
import emu.netcore.message.OutgoingMessage
import emu.protocol.osrs239.game.message.MessagePublic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagePublicHandlerTest {
    @Test fun `IO handler normalizes text but content executes only from the cycle mailbox`() = runBlocking {
        val huffman = HuffmanCodec(ByteArray(256) { 8 })
        val queue = PlayerChatQueue()
        val handled = mutableListOf<ChatInput>()
        val actions: ChatActionRegistry = chatActions { onPublicMessage { handled += it } }

        MessagePublicHandler(huffman, queue).handle(
            MessagePublic(0, 0, 0, huffman.encode("  hello   world  ")),
            NoOutput,
        )

        assertTrue(handled.isEmpty())
        queue.cycleProcesses(actions).single().process(0)
        assertEquals("hello world", (handled.single() as emu.game.chat.PublicChatInput).text)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) = error("chat input must not write from the IO coroutine")
    }
}
