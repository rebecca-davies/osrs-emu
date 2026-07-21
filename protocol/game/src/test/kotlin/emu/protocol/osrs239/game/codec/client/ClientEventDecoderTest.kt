package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.client.EventAppletFocus
import emu.protocol.osrs239.game.message.client.NoTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientEventDecoderTest {
    @Test
    fun `decodes focused and unfocused applet events`() {
        assertEquals(
            EventAppletFocus(focused = true),
            EventAppletFocusDecoder.decode(JagexBuffer(byteArrayOf(1))),
        )
        assertEquals(
            EventAppletFocus(focused = false),
            EventAppletFocusDecoder.decode(JagexBuffer(byteArrayOf(0))),
        )
    }

    @Test
    fun `decodes the zero-body connection keepalive`() {
        assertEquals(NoTimeout, NoTimeoutDecoder.decode(JagexBuffer(ByteArray(0))))
    }

    @Test
    fun `rejects malformed fixed client-event bodies`() {
        assertFailsWith<IllegalArgumentException> {
            EventAppletFocusDecoder.decode(JagexBuffer(ByteArray(0)))
        }
        assertFailsWith<IllegalArgumentException> {
            NoTimeoutDecoder.decode(JagexBuffer(byteArrayOf(0)))
        }
    }
}
