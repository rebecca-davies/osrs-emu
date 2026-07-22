package emu.protocol.osrs239.game.codec.loc

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.loc.OpLoc1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpLoc1DecoderTest {
    @Test
    fun `decodes the challenge portal packet written by the rev 239 client`() {
        val payload =
            byteArrayOf(
                0x0E,
                0xA4.toByte(),
                0x36,
                0x0C,
                0x81.toByte(),
                0x80.toByte(),
                0x68,
                0x12,
            )

        assertEquals(
            OpLoc1(type = 26_642, x = 3_126, z = 3_620, subOption = 0, keyCombination = 1),
            OpLoc1Decoder.decode(JagexBuffer(payload)),
        )
    }

    @Test
    fun `rejects a loc operation body outside its fixed boundary`() {
        assertFailsWith<IllegalArgumentException> {
            OpLoc1Decoder.decode(JagexBuffer(ByteArray(7)))
        }
    }
}
