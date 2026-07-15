package emu.cache.store

import java.io.File

/** Reads cache groups from the repository's flat-file cache layout. */
class FlatFileStore(private val root: File) : Store {
    override fun read(archive: Int, group: Int): ByteArray? {
        val f = File(root, "cache/$archive/$group.dat")
        return if (f.isFile) f.readBytes() else null
    }
}
