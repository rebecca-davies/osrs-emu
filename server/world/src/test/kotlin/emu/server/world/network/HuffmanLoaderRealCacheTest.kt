package emu.server.world.network

import emu.cache.store.FlatFileStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class HuffmanLoaderRealCacheTest {
    @Test fun `build 239 cache Huffman table round trips public chat text when cache data exists`() {
        val root =
            listOf(File("../cache-data"), File("../../osrsemu/cache-data"))
                .firstOrNull { File(it, "cache/10/1.dat").isFile }
                ?: run { println("SKIP: no build-239 cache-data"); return }
        val codec = loadHuffmanCodec(FlatFileStore(root))

        assertEquals("Hello from Lumbridge!", codec.decode(codec.encode("Hello from Lumbridge!")))
    }
}
