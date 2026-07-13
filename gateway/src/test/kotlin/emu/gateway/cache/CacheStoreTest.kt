package emu.gateway.cache

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class CacheStoreTest {
    private fun tempRoot(): File = Files.createTempDirectory("cachestore").toFile()

    @Test fun `returns group bytes when the file exists`() {
        val root = tempRoot()
        val f = File(root, "cache/255/255.dat")
        f.parentFile.mkdirs()
        val bytes = byteArrayOf(0, 1, 2, 3, 4)
        f.writeBytes(bytes)
        val store = CacheStore(root)
        assertContentEquals(bytes, store.group(255, 255))
    }

    @Test fun `returns null for a missing group`() {
        val store = CacheStore(tempRoot())
        assertNull(store.group(5, 99999))
    }
}
