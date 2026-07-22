package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.codec.component.CloseModalDecoder
import emu.protocol.osrs239.game.message.client.Idle
import emu.protocol.osrs239.game.message.component.CloseModal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerControlDecoderTest {
    @Test
    fun `decodes zero-body modal close and idle requests`() {
        assertEquals(CloseModal, CloseModalDecoder.decode(JagexBuffer(ByteArray(0))))
        assertEquals(Idle, IdleDecoder.decode(JagexBuffer(ByteArray(0))))
    }

    @Test
    fun `rejects bytes outside the fixed zero-body contracts`() {
        assertFailsWith<IllegalArgumentException> {
            CloseModalDecoder.decode(JagexBuffer(byteArrayOf(0)))
        }
        assertFailsWith<IllegalArgumentException> {
            IdleDecoder.decode(JagexBuffer(byteArrayOf(0)))
        }
    }
}
