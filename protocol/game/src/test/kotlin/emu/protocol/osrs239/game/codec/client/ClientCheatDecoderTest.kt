package emu.protocol.osrs239.game.codec.client

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.prot.GameClientProt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientCheatDecoderTest {
    @Test
    fun `decodes the NUL-terminated command body from rev 239`() {
        val body = "addbot 12".toByteArray(Charsets.ISO_8859_1) + 0

        val message = ClientCheatDecoder.decode(JagexBuffer(body))

        assertEquals(GameClientProt.CLIENT_CHEAT, ClientCheatDecoder.prot)
        assertEquals("addbot 12", message.input)
    }

    @Test
    fun `rejects unterminated and oversized command bodies`() {
        assertFailsWith<IllegalArgumentException> {
            ClientCheatDecoder.decode(JagexBuffer("addbot 1".toByteArray()))
        }
        assertFailsWith<IllegalArgumentException> {
            ClientCheatDecoder.decode(JagexBuffer(ByteArray(82)))
        }
    }
}
