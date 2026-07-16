package emu.cache.index.codec

import emu.buffer.JagexBuffer
import emu.cache.index.model.GroupEntry
import emu.cache.index.model.Js5Index

/**
 * Encodes a JS5 reference table in the positional field order consumed by [Js5IndexDecoder].
 */
object Js5IndexEncoder {
    fun encode(index: Js5Index): ByteArray {
        val protocol = index.protocol
        val named = index.named
        val sized = index.sized
        val groups = index.groups

        assertAscendingGroupIds(groups)
        for (g in groups) assertAscendingFileIds(g)

        val buf = JagexBuffer.alloc(sizeOf(index))

        buf.writeByte(protocol)
        if (protocol >= 6) buf.writeInt(index.revision)

        buf.writeByte(index.flags)
        writeIndexField(buf, protocol, groups.size)

        var lastId = 0
        for (g in groups) {
            writeIndexField(buf, protocol, g.id - lastId)
            lastId = g.id
        }

        if (named) for (g in groups) buf.writeInt(g.nameHash)

        for (g in groups) buf.writeInt(g.crc)

        if (sized) {
            for (g in groups) {
                buf.writeInt(g.compressedSize)
                buf.writeInt(g.decompressedSize)
            }
        }

        for (g in groups) buf.writeInt(g.revision)

        for (g in groups) writeIndexField(buf, protocol, g.files.size)

        for (g in groups) {
            var last = 0
            for (f in g.files) {
                writeIndexField(buf, protocol, f.id - last)
                last = f.id
            }
        }

        if (named) {
            for (g in groups) {
                for (f in g.files) buf.writeInt(f.nameHash)
            }
        }

        return buf.array
    }

    private fun assertAscendingGroupIds(groups: List<GroupEntry>) {
        for (i in 1 until groups.size) {
            require(groups[i].id > groups[i - 1].id) { "group ids out of order at index $i" }
        }
    }

    private fun assertAscendingFileIds(group: GroupEntry) {
        for (i in 1 until group.files.size) {
            require(group.files[i].id > group.files[i - 1].id) {
                "file ids out of order in group ${group.id} at index $i"
            }
        }
    }

    private fun sizeOf(index: Js5Index): Int {
        val protocol = index.protocol
        val groups = index.groups

        var size = 1 // protocol
        if (protocol >= 6) size += 4 // revision
        size += 1 // flags
        size += indexFieldSize(protocol, groups.size)

        var lastId = 0
        for (g in groups) {
            size += indexFieldSize(protocol, g.id - lastId)
            lastId = g.id
        }

        if (index.named) size += groups.size * 4 // group name hashes
        size += groups.size * 4 // crcs
        if (index.sized) size += groups.size * 8 // compressed + decompressed sizes
        size += groups.size * 4 // revisions

        for (g in groups) size += indexFieldSize(protocol, g.files.size)

        for (g in groups) {
            var last = 0
            for (f in g.files) {
                size += indexFieldSize(protocol, f.id - last)
                last = f.id
            }
        }

        if (index.named) {
            for (g in groups) size += g.files.size * 4 // file name hashes
        }

        return size
    }
}
