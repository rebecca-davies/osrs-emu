package emu.cache.store

/** Provides raw cache containers by archive and group identifier. */
interface Store {
    fun read(archive: Int, group: Int): ByteArray?
}
