package emu.cache.store

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class FlatFileStoreTest {
    private fun tempRoot(): File = Files.createTempDirectory("store").toFile()

    @Test fun `reads group bytes when present`() {
        val root = tempRoot()
        val f = File(root, "cache/255/255.dat"); f.parentFile.mkdirs()
        f.writeBytes(byteArrayOf(9, 8, 7))
        val store: Store = FlatFileStore(root)
        assertContentEquals(byteArrayOf(9, 8, 7), store.read(255, 255))
    }

    @Test fun `returns null when absent`() {
        assertNull(FlatFileStore(tempRoot()).read(5, 42))
    }
}
