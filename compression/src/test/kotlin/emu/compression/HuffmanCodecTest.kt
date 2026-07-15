package emu.compression

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HuffmanCodecTest {
    private val identity = HuffmanCodec(ByteArray(256) { 8 })

    @Test fun `round trips cp1252 text with the protocol smart length prefix`() {
        val encoded = identity.encode("Hello world!")

        assertContentEquals(byteArrayOf(12) + "Hello world!".toByteArray(Charsets.ISO_8859_1), encoded)
        assertEquals("Hello world!", identity.decode(encoded))
    }

    @Test fun `rejects truncated and oversized encoded text`() {
        assertFailsWith<IllegalArgumentException> { identity.decode(byteArrayOf(5, 'h'.code.toByte())) }
        assertFailsWith<IllegalArgumentException> { identity.decode(byteArrayOf(101) + ByteArray(101), maxDecodedBytes = 100) }
    }
}
