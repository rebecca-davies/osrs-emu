package emu.protocol.osrs239.game.codec

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerPublicChat
import kotlin.test.Test
import kotlin.test.assertContentEquals

class PlayerPublicChatEncoderTest {
    @Test fun `encodes rev 239 chat extended info flags transforms and reversed huffman data`() {
        val encodedText = byteArrayOf(5, 'h'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte())
        val body = PlayerInfoEncoder.encode(
            NopStreamCipher,
            PlayerInfo(publicChat = PlayerPublicChat(1, 2, 0, encodedText)),
        )

        assertContentEquals(
            byteArrayOf(
                0x08, 0x01, // CHAT plus EXTENDED_SHORT flags
                0x01, 0x82.toByte(), // p2Alt2 colour/effect
                0x00, 0x80.toByte(), 0xFA.toByte(), // mod icon, autotyper, encoded length
                'o'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(), 'h'.code.toByte(), 5,
            ),
            body.takeLast(13).toByteArray(),
        )
    }

    @Test fun `cached movement speed precedes chat in the combined extended info block`() {
        val body =
            PlayerInfoEncoder.encode(
                NopStreamCipher,
                PlayerInfo(
                    moveSpeed = 2,
                    publicChat = PlayerPublicChat(0, 0, 0, byteArrayOf(1, 'x'.code.toByte())),
                ),
            )

        assertContentEquals(
            byteArrayOf(
                0x08, 0x05, // EXTENDED_SHORT + CHAT + MOVE_SPEED
                0xFE.toByte(), // cached run via p1Alt2
                0x00, 0x80.toByte(), // p2Alt2 colour/effect
                0x00, 0x80.toByte(), 0xFE.toByte(), // mod icon, autotyper, encoded length
                'x'.code.toByte(), 1, // reversed encoded text
            ),
            body.copyOfRange(3, body.size),
        )
    }
}
