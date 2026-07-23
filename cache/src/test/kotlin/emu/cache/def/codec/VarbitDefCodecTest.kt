package emu.cache.def.codec

import emu.cache.def.VarbitDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VarbitDefCodecTest {
    @Test
    fun `opcode one decodes the base varp and inclusive bit range`() {
        assertEquals(
            VarbitDefinition(id = 8119, baseVar = 1141, bits = 31..31),
            VarbitDefCodec.decode(8119, byteArrayOf(1, 4, 117, 31, 31, 0)),
        )
    }

    @Test
    fun `unknown fields and missing definitions are rejected`() {
        assertFailsWith<IllegalStateException> { VarbitDefCodec.decode(1, byteArrayOf(2, 0)) }
        assertFailsWith<IllegalArgumentException> { VarbitDefCodec.decode(1, byteArrayOf(0)) }
    }
}
