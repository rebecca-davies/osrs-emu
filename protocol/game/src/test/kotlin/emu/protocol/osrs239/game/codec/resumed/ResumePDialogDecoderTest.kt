package emu.protocol.osrs239.game.codec.resumed

import emu.buffer.JagexBuffer
import emu.protocol.osrs239.game.message.resumed.ResumePCountDialog
import emu.protocol.osrs239.game.message.resumed.ResumePObjDialog
import kotlin.test.Test
import kotlin.test.assertEquals

class ResumePDialogDecoderTest {
    @Test
    fun `decodes the revision 239 count dialog response`() {
        assertEquals(
            ResumePCountDialog(99),
            ResumePCountDialogDecoder.decode(JagexBuffer(byteArrayOf(0, 0, 0, 99))),
        )
    }

    @Test
    fun `preserves the raw unsigned object dialog selection`() {
        assertEquals(
            ResumePObjDialog(4_151),
            ResumePObjDialogDecoder.decode(JagexBuffer(byteArrayOf(0x10, 0x37))),
        )
        assertEquals(
            ResumePObjDialog(0xFFFF),
            ResumePObjDialogDecoder.decode(JagexBuffer(byteArrayOf(-1, -1))),
        )
    }
}
