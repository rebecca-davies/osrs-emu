package emu.cache.index.checksum

import emu.cache.index.model.GroupEntry
import java.util.zip.CRC32

/** Standard CRC-32 of [bytes], as stored per group in a JS5 reference table. */
fun crc32(bytes: ByteArray): Int {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value.toInt()
}

/** Returns this group with its container CRC recomputed and, by default, its revision incremented. */
fun GroupEntry.withRecomputedCrc(
    newContainerBytes: ByteArray,
    bumpRevision: Boolean = true,
): GroupEntry =
    copy(
        crc = crc32(newContainerBytes),
        revision = if (bumpRevision) revision + 1 else revision,
    )
