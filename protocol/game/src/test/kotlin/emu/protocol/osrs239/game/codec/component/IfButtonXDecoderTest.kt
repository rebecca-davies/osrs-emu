package emu.protocol.osrs239.game.codec.component

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.component.IfButtonX
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IfButtonXDecoderTest {
    @Test fun `decodes the captured rev 239 logout button`() {
        val body = byteArrayOf(0x00, 0xB6.toByte(), 0x00, 0x08, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x01)

        assertEquals(
            IfButtonX(combinedId = 0x00B60008, sub = -1, obj = -1, op = 1),
            IfButtonXDecoder.decode(JagexBuffer(body)),
        )
    }

    @Test fun `rejects an if button x body that is not nine bytes`() {
        assertFailsWith<IllegalArgumentException> {
            IfButtonXDecoder.decode(JagexBuffer(ByteArray(8)))
        }
    }
}
