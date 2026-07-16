package emu.protocol.osrs239.game.codec.chat

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.chat.MessagePublic
import emu.protocol.osrs239.game.message.chat.SetChatFilterSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChatInputDecoderTest {
    @Test fun `decodes rev 239 public chat envelope without interpreting huffman bytes`() {
        val encoded = byteArrayOf(5, 1, 2, 3, 4)
        val body = byteArrayOf(0, 13, 2, 9) + encoded

        val message = MessagePublicDecoder.decode(JagexBuffer(body))

        assertEquals(0, message.type)
        assertEquals(13, message.colour)
        assertEquals(2, message.effect)
        assertContentEquals(byteArrayOf(9), message.pattern)
        assertContentEquals(encoded, message.encodedText)
    }

    @Test fun `decodes the three captured chat filter bytes`() {
        assertEquals(
            SetChatFilterSettings(publicFilter = 3, privateFilter = 1, tradeFilter = 2),
            SetChatFilterSettingsDecoder.decode(JagexBuffer(byteArrayOf(3, 1, 2))),
        )
    }
}
