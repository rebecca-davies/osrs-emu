package emu.cache.map.codec

import emu.cache.map.model.MapLocSpawn

/** Decodes delta-smart static loc placements from file 1 of a packed rev-239 map-square group. */
object MapLocDecoder {
    fun decode(data: ByteArray): List<MapLocSpawn> {
        val input = SmartInput(data)
        val result = ArrayList<MapLocSpawn>(data.size / 2)
        var id = -1
        while (input.readable) {
            val idOffset = input.readIncrShortSmart()
            if (idOffset == 0) break
            id = Math.addExact(id, idOffset)

            var packedTile = 0
            while (input.readable) {
                val tileOffset = input.readShortSmart()
                if (tileOffset == 0) break
                packedTile = Math.addExact(packedTile, tileOffset - 1)
                require(packedTile in 0..MAX_PACKED_TILE) { "loc $id has invalid packed tile $packedTile" }
                val attributes = input.readUByte()
                result += MapLocSpawn(
                    id = id,
                    localX = packedTile shr 6 and 0x3F,
                    localY = packedTile and 0x3F,
                    plane = packedTile shr 12 and 0x3,
                    shape = attributes shr 2,
                    rotation = attributes and 0x3,
                )
            }
        }
        require(!input.readable) { "loc group has ${input.remaining} trailing byte(s)" }
        return result
    }

    private class SmartInput(private val data: ByteArray) {
        private var position = 0
        val readable: Boolean get() = position < data.size
        val remaining: Int get() = data.size - position

        fun readUByte(): Int {
            require(remaining >= 1) { "loc group ended inside byte" }
            return data[position++].toInt() and 0xFF
        }

        fun readShortSmart(): Int {
            require(remaining >= 1) { "loc group ended inside short smart" }
            val first = data[position].toInt() and 0xFF
            if (first < 128) return readUByte()
            require(remaining >= 2) { "loc group ended inside two-byte short smart" }
            return (readUByte() shl 8 or readUByte()) - 32_768
        }

        fun readIncrShortSmart(): Int {
            var total = 0
            do {
                val part = readShortSmart()
                total = Math.addExact(total, part)
            } while (part == 32_767)
            return total
        }
    }

    private const val MAX_PACKED_TILE = (3 shl 12) or (63 shl 6) or 63
}
