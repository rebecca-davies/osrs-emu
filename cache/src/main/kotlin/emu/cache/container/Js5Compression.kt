package emu.cache.container

/** Compression scheme stored in byte zero of a [Container]. */
enum class Js5Compression(val id: Int) {
    NONE(0),
    BZIP2(1),
    GZIP(2);

    companion object {
        fun fromId(id: Int): Js5Compression =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown compression type: $id")
    }
}
