package emu.buffer

import kotlin.test.Test
import kotlin.test.assertEquals

class JagexBufferCStringBoundsTest {
    @Test fun `cstring without a terminator reads to end of buffer without overrun`() {
        // 'a','b','c' with NO trailing null — previously an ArrayIndexOutOfBoundsException.
        val b = JagexBuffer(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()))
        assertEquals("abc", b.readCString())
        assertEquals(3, b.pos) // cursor at end, not past it
    }
}
