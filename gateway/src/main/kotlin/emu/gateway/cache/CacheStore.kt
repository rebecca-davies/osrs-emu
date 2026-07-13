package emu.gateway.cache

import java.io.File

class CacheStore(private val root: File) {
    fun group(archive: Int, group: Int): ByteArray? {
        val f = File(root, "cache/$archive/$group.dat")
        return if (f.isFile) f.readBytes() else null
    }
}
