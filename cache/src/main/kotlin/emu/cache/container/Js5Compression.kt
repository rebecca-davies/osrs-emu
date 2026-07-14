package emu.cache.container

/**
 * The compression scheme stamped in byte 0 of every [Container] (recon doc §1,
 * `fs/jagex/CompressionType.java`).
 */
enum class Js5Compression(val id: Int) {
    NONE(0),
    BZIP2(1),
    GZIP(2);

    companion object {
        fun fromId(id: Int): Js5Compression =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown compression type: $id")
    }
}
