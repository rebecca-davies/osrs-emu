package emu.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamCipherTest {
    @Test fun `nop cipher always yields zero`() {
        val c: StreamCipher = NopStreamCipher
        assertEquals(0, c.nextInt())
        assertEquals(0, c.nextInt())
    }

    @Test fun `isaac is usable through the interface`() {
        val c: StreamCipher = IsaacCipher(intArrayOf(1, 2, 3, 4))
        // first value equals the pinned golden's first entry
        val golden = this::class.java.getResourceAsStream("/isaac-golden.txt")!!
            .bufferedReader().readLines().first { it.isNotBlank() }.trim().toInt()
        assertEquals(golden, c.nextInt())
    }
}
