package emu.protocol.osrs239.game.codec.chat

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.message.chat.MessageGame
import kotlin.test.Test
import kotlin.test.assertEquals

/** Byte vectors are the inverse of rsprox rev-239's `MessageGameDecoder` (smart type, name flag, jstrs). */
class MessageGameEncoderTest {
    private fun cp1252(s: String) = s.toByteArray(charset("windows-1252")).toList()

    @Test fun `plain game message is smart type, no-name flag, then the cp1252 nul-terminated text`() {
        val body = MessageGameEncoder.encode(
            NopStreamCipher,
            MessageGame(MessageGame.GAME_MESSAGE, "Welcome to RuneScape."),
        )
        val expected = buildList<Byte> {
            add(0) // smart type 0 (chattype_gamemessage) fits in one byte
            add(0) // no sender name
            addAll(cp1252("Welcome to RuneScape.")); add(0)
        }
        assertEquals(expected, body.toList())
        assertEquals(24, body.size)
    }

    @Test fun `request-style message carries the sender name behind the name-present flag`() {
        val body = MessageGameEncoder.encode(
            NopStreamCipher,
            MessageGame(101, "wishes to trade with you.", name = "Bob"),
        )
        val expected = buildList<Byte> {
            add(101) // smart type 101 (< 128) is one byte
            add(1)   // name present
            addAll(cp1252("Bob")); add(0)
            addAll(cp1252("wishes to trade with you.")); add(0)
        }
        assertEquals(expected, body.toList())
    }

    @Test fun `message type at or above 128 uses the two-byte smart form`() {
        val body = MessageGameEncoder.encode(NopStreamCipher, MessageGame(200, "x"))
        assertEquals(
            listOf(0x80, 0xC8, 0, 'x'.code, 0).map(Int::toByte), // 200 + 0x8000 = 0x80C8
            body.toList(),
        )
    }
}
