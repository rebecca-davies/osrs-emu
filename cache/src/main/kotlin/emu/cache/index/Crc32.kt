package emu.cache.index

import java.util.zip.CRC32

/** Standard CRC-32 of [bytes], as stored per-group in a JS5 reference table (recon doc §3/§6). */
fun crc32(bytes: ByteArray): Int {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value.toInt()
}

/**
 * Returns [this] with `crc` recomputed from [newContainerBytes] (the group's re-encoded
 * container — CRC is a function of the container, not the raw group contents, recon doc §1) and
 * `revision` bumped by one, ready to be written back into the owning [Js5Index].
 */
fun GroupEntry.withRecomputedCrc(newContainerBytes: ByteArray, bumpRevision: Boolean = true): GroupEntry =
    copy(crc = crc32(newContainerBytes), revision = if (bumpRevision) revision + 1 else revision)
