package emu.cache.index

import emu.cache.container.Container
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Strongest available round-trip check: decode a real reference table pulled from a genuine
 * rev-239 cache dump and re-encode it, asserting the re-encoded bytes are identical to the
 * decompressed original.
 *
 * `cache-data/` is gitignored (CLAUDE.md §14/§15 — no cache dump committed) and may be absent on a
 * given machine or in CI, so every test here guards on the file's presence and returns early
 * (passing, not failing) when it's missing.
 *
 * Scope note: this compares the *decompressed* IndexData bytes, not the outer compressed
 * container. Re-compressing with Apache Commons Compress's bzip2 encoder is not guaranteed to
 * reproduce Jagex's original compressed bytes bit-for-bit (recon doc §6/§1) — only the positional
 * integer encoding inside [Js5IndexDecoder]/[Js5IndexEncoder] is required to be byte-exact, and
 * that's what this test proves.
 */
class Js5IndexRealCacheDataTest {
    private val referenceTableFile = File("../cache-data/cache/255/2.dat")

    @Test fun `real index-2 reference table re-encodes byte-identically`() {
        if (!referenceTableFile.isFile) return // cache-data not present on this machine; skip

        val bytes = referenceTableFile.readBytes()
        val container = Container.decode(bytes)

        val index = Js5IndexDecoder.decode(container.data)
        val reEncoded = Js5IndexEncoder.encode(index)

        assertContentEquals(container.data, reEncoded)
    }
}
