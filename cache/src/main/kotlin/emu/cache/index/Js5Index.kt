package emu.cache.index

/** JS5 reference-table flag bits. */
object Js5IndexFlags {
    const val NAMED: Int = 0x1
    const val SIZED: Int = 0x4
    const val ALL: Int = NAMED or SIZED
}

/** A file within a [GroupEntry]. `nameHash` is 0 (unused) unless the owning index is [Js5Index.named]. */
data class FileEntry(val id: Int, val nameHash: Int = 0)

/**
 * One archive/group entry inside a JS5 reference table. `compressedSize`/`decompressedSize` are 0
 * (unused) unless the owning index is [Js5Index.sized]; `nameHash` is 0 (unused) unless [Js5Index.named].
 */
data class GroupEntry(
    val id: Int,
    val nameHash: Int = 0,
    val crc: Int,
    val revision: Int,
    val compressedSize: Int = 0,
    val decompressedSize: Int = 0,
    val files: List<FileEntry>,
)

/**
 * A decoded JS5 reference table (index 255, archive X): describes every group of top-level index
 * `X`: ids, name hashes, CRCs, revisions, and child file ids.
 *
 * `groups` must be ascending by [GroupEntry.id], and each group's `files` ascending by
 * [FileEntry.id] — [Js5IndexEncoder] asserts this, mirroring the client's own invariant.
 */
data class Js5Index(
    val protocol: Int,
    val revision: Int,
    val flags: Int,
    val groups: List<GroupEntry>,
) {
    init {
        require(protocol in 5..7) { "Unsupported JS5 index protocol: $protocol" }
        require(flags and Js5IndexFlags.ALL.inv() == 0) { "Unknown JS5 index flags: $flags" }
    }

    val named: Boolean get() = (flags and Js5IndexFlags.NAMED) != 0
    val sized: Boolean get() = (flags and Js5IndexFlags.SIZED) != 0
}
