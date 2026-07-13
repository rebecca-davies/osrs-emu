package emu.cache.store

interface Store {
    fun read(archive: Int, group: Int): ByteArray?
}
