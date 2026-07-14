package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.MoveGameClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoveGameClickDecoderTest {
    @Test
    fun `decodes rev 239 little-endian coordinates and alternate key byte`() {
        val payload = byteArrayOf(0x96.toByte(), 0x0C, 0x92.toByte(), 0x0C, 0x7F)

        assertEquals(
            MoveGameClick(x = 3222, z = 3218, keyCombination = 1),
            MoveGameClickDecoder.decode(JagexBuffer(payload)),
        )
    }

    @Test
    fun `rejects a movement body whose declared variable length is not five`() {
        assertFailsWith<IllegalArgumentException> {
            MoveGameClickDecoder.decode(JagexBuffer(ByteArray(4)))
        }
    }
}
