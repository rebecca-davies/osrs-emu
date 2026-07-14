package emu.cache.index

import emu.buffer.JagexBuffer

/**
 * Decodes a JS5 reference table from its decompressed [emu.cache.container.Container] payload
 * (recon doc §3, `IndexData.load`). Field read order is positional and MUST match
 * [Js5IndexEncoder] exactly.
 */
object Js5IndexDecoder {
    fun decode(bytes: ByteArray): Js5Index {
        val buf = JagexBuffer(bytes)
        val protocol = buf.readUByte()
        require(protocol in 5..7) { "Unsupported JS5 index protocol: $protocol" }

        val revision = if (protocol >= 6) buf.readInt() else 0

        val flags = buf.readUByte()
        require(flags and Js5IndexFlags.ALL.inv() == 0) { "Unknown JS5 index flags: $flags" }
        val named = (flags and Js5IndexFlags.NAMED) != 0
        val sized = (flags and Js5IndexFlags.SIZED) != 0

        val groupCount = readIndexField(buf, protocol)

        val ids = IntArray(groupCount)
        var lastId = 0
        for (i in 0 until groupCount) {
            lastId += readIndexField(buf, protocol)
            ids[i] = lastId
        }

        val nameHashes = IntArray(groupCount)
        if (named) {
            for (i in 0 until groupCount) nameHashes[i] = buf.readInt()
        }

        val crcs = IntArray(groupCount)
        for (i in 0 until groupCount) crcs[i] = buf.readInt()

        val compressedSizes = IntArray(groupCount)
        val decompressedSizes = IntArray(groupCount)
        if (sized) {
            for (i in 0 until groupCount) {
                compressedSizes[i] = buf.readInt()
                decompressedSizes[i] = buf.readInt()
            }
        }

        val revisions = IntArray(groupCount)
        for (i in 0 until groupCount) revisions[i] = buf.readInt()

        val fileCounts = IntArray(groupCount)
        for (i in 0 until groupCount) fileCounts[i] = readIndexField(buf, protocol)

        val fileIds = Array(groupCount) { IntArray(fileCounts[it]) }
        for (i in 0 until groupCount) {
            var last = 0
            for (j in 0 until fileCounts[i]) {
                last += readIndexField(buf, protocol)
                fileIds[i][j] = last
            }
        }

        val fileNameHashes = Array(groupCount) { IntArray(fileCounts[it]) }
        if (named) {
            for (i in 0 until groupCount) {
                for (j in 0 until fileCounts[i]) {
                    fileNameHashes[i][j] = buf.readInt()
                }
            }
        }

        val groups = (0 until groupCount).map { i ->
            val files = (0 until fileCounts[i]).map { j ->
                FileEntry(fileIds[i][j], if (named) fileNameHashes[i][j] else 0)
            }
            GroupEntry(
                id = ids[i],
                nameHash = if (named) nameHashes[i] else 0,
                crc = crcs[i],
                revision = revisions[i],
                compressedSize = if (sized) compressedSizes[i] else 0,
                decompressedSize = if (sized) decompressedSizes[i] else 0,
                files = files,
            )
        }

        return Js5Index(protocol, revision, flags, groups)
    }
}
