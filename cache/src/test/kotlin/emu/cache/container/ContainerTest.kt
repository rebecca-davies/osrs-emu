package emu.cache.container

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContainerTest {
    private val payload = "the quick brown fox jumps over the lazy dog, 0123456789".toByteArray()

    @Test fun `NONE round-trips`() {
        val encoded = Container.encode(Js5Compression.NONE, payload)
        val decoded = Container.decode(encoded)
        assertEquals(Js5Compression.NONE, decoded.compression)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `BZIP2 round-trips`() {
        val encoded = Container.encode(Js5Compression.BZIP2, payload)
        val decoded = Container.decode(encoded)
        assertEquals(Js5Compression.BZIP2, decoded.compression)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `GZIP round-trips`() {
        val encoded = Container.encode(Js5Compression.GZIP, payload)
        val decoded = Container.decode(encoded)
        assertEquals(Js5Compression.GZIP, decoded.compression)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `revision trailer round-trips`() {
        val encoded = Container.encode(Js5Compression.GZIP, payload, revision = 42)
        val decoded = Container.decode(encoded)
        assertEquals(42, decoded.revision)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `absent revision decodes as -1`() {
        val encoded = Container.encode(Js5Compression.NONE, payload)
        val decoded = Container.decode(encoded)
        assertEquals(Container.NO_REVISION, decoded.revision)
    }

    @Test fun `ZERO key is identity - encoding with and without a key is the same bytes`() {
        val withZero = Container.encode(Js5Compression.NONE, payload, XteaKey.ZERO)
        val withoutKey = Container.encode(Js5Compression.NONE, payload)
        assertContentEquals(withoutKey, withZero)
    }

    @Test fun `non-zero XTEA key round-trips and changes the wire bytes`() {
        val key = XteaKey(0x00010203, 0x04050607, 0x08090a0b, 0x0c0d0e0f)
        val encoded = Container.encode(Js5Compression.NONE, payload, key)
        val plain = Container.encode(Js5Compression.NONE, payload, XteaKey.ZERO)
        assertFalse(encoded.contentEquals(plain), "XTEA-encrypted payload should differ from plaintext")

        val decoded = Container.decode(encoded, key)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `non-zero XTEA key on BZIP2 payload round-trips`() {
        val key = XteaKey(1, 2, 3, 4)
        val encoded = Container.encode(Js5Compression.BZIP2, payload, key)
        val decoded = Container.decode(encoded, key)
        assertContentEquals(payload, decoded.data)
    }

    @Test fun `rejects unknown compression id`() {
        val bytes = byteArrayOf(9, 0, 0, 0, 0)
        kotlin.test.assertFailsWith<IllegalArgumentException> { Container.decode(bytes) }
    }

    @Test fun `rejects negative compressed length`() {
        val bytes = byteArrayOf(0, -1, -1, -1, -1)
        kotlin.test.assertFailsWith<IllegalArgumentException> { Container.decode(bytes) }
    }
}
