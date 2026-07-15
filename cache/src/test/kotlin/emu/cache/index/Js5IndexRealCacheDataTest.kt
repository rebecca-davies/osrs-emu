package emu.cache.index

import emu.cache.container.Container
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Round-trips a real rev-239 reference table when the optional cache fixture exists.
 * The assertion covers decompressed index bytes, not compressor-specific outer-container bytes.
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
