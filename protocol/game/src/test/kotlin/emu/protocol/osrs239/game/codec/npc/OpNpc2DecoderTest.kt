package emu.protocol.osrs239.game.codec.npc

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.npc.OpNpc2
import emu.protocol.osrs239.game.prot.GameClientProt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpNpc2DecoderTest {
    @Test
    fun `decodes the rev 239 index sub-option and control transforms`() {
        val body = byteArrayOf(0xB4.toByte(), 0x12, 0xF9.toByte(), 0x7F)

        assertEquals(
            OpNpc2(index = 0x1234, subOption = 7, controlKey = true),
            OpNpc2Decoder.decode(JagexBuffer(body)),
        )
        assertEquals(GameClientProt.OPNPC2, OpNpc2Decoder.prot)
    }

    @Test
    fun `rejects a body that is not exactly four bytes`() {
        assertFailsWith<IllegalArgumentException> {
            OpNpc2Decoder.decode(JagexBuffer(ByteArray(3)))
        }
    }
}
