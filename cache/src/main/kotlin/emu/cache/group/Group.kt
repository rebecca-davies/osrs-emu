package emu.cache.group

import emu.buffer.JagexBuffer

/**
 * Multi-file group framing inside a decompressed [emu.cache.container.Container] payload.
 *
 * A group with one file has no framing at all; a group with more than one file ends with a
 * delta-encoded per-chunk size table and a trailing chunk-count byte. Files are addressed by their
 * 0-based position within the group (ascending file-id order) — the caller maps position to the
 * real file id using the reference table's `fileIds` list.
 */
object Group {
    /**
     * Splits [payload] (the decompressed container data for a group of [fileCount] files) back
     * into per-file byte ranges, keyed by 0-based position (ascending file-id order).
     */
    fun unpack(payload: ByteArray, fileCount: Int): Map<Int, ByteArray> {
        require(fileCount > 0) { "fileCount must be positive, got $fileCount" }
        if (fileCount == 1) {
            return mapOf(0 to payload)
        }

        val trailer = JagexBuffer(payload, pos = payload.size - 1)
        val chunks = trailer.readUByte()

        val tableStart = payload.size - 1 - chunks * fileCount * 4
        require(tableStart >= 0) { "Group payload too small for $chunks chunk(s) x $fileCount file(s)" }

        val table = JagexBuffer(payload, pos = tableStart)
        val chunkSizes = Array(fileCount) { IntArray(chunks) }
        val fileSizes = IntArray(fileCount)

        for (chunk in 0 until chunks) {
            var chunkSize = 0
            for (id in 0 until fileCount) {
                chunkSize += table.readInt()
                chunkSizes[id][chunk] = chunkSize
                fileSizes[id] += chunkSize
            }
        }

        val fileContents = Array(fileCount) { ByteArray(fileSizes[it]) }
        val fileOffsets = IntArray(fileCount)

        val reader = JagexBuffer(payload, pos = 0)
        for (chunk in 0 until chunks) {
            for (id in 0 until fileCount) {
                val size = chunkSizes[id][chunk]
                reader.readBytes(size).copyInto(fileContents[id], fileOffsets[id])
                fileOffsets[id] += size
            }
        }

        return (0 until fileCount).associateWith { fileContents[it] }
    }

    /**
     * Packs [files] (keyed ascending, e.g. by file id) into a group payload: file contents
     * concatenated in ascending-key order, followed (when there's more than one file) by a single
     * delta-encoded chunk-size table and a trailing chunk-count byte of `1`.
     */
    fun pack(files: Map<Int, ByteArray>): ByteArray {
        require(files.isNotEmpty()) { "cannot pack an empty group" }
        val ordered = files.toSortedMap().values.toList()

        if (ordered.size == 1) {
            return ordered[0].copyOf()
        }

        val contentSize = ordered.sumOf { it.size }
        val out = JagexBuffer.alloc(contentSize + ordered.size * 4 + 1)

        for (file in ordered) out.writeBytes(file)

        var previousSize = 0
        for (file in ordered) {
            out.writeInt(file.size - previousSize)
            previousSize = file.size
        }

        out.writeByte(1)
        return out.array
    }
}
