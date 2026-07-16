package emu.cache.map.codec

import emu.buffer.JagexBuffer
import emu.cache.map.model.MapTileFlags

/** Decodes movement rules from file 0 of a packed rev-239 map-square group. */
object MapTileDecoder {
    fun decode(data: ByteArray): MapTileFlags {
        val input = JagexBuffer(data)
        val result = MapTileFlags()
        for (plane in 0 until MapTileFlags.PLANE_COUNT) {
            for (localX in 0 until MapTileFlags.MAP_SQUARE_SIZE) {
                for (localY in 0 until MapTileFlags.MAP_SQUARE_SIZE) {
                    decodeTile(input, result, localX, localY, plane)
                }
            }
        }
        require(input.readableBytes() == 1) {
            "terrain file has ${input.readableBytes()} byte(s) after its tile data"
        }
        require(input.readUByte() == FILE_TERMINATOR) { "terrain file has an invalid terminator" }
        return result
    }

    private fun decodeTile(
        input: JagexBuffer,
        result: MapTileFlags,
        localX: Int,
        localY: Int,
        plane: Int,
    ) {
        while (true) {
            require(input.readableBytes() >= 2) { "terrain group ended inside tile $localX,$localY,$plane" }
            val opcode = input.readUShort()
            when {
                opcode == 0 -> return
                opcode == 1 -> {
                    require(input.readableBytes() >= 1) { "terrain height missing at $localX,$localY,$plane" }
                    input.readUByte()
                    return
                }
                opcode <= 49 -> {
                    require(input.readableBytes() >= 2) { "terrain overlay missing at $localX,$localY,$plane" }
                    input.readUShort()
                }
                opcode <= 81 -> {
                    val rules = opcode - 49
                    if (rules and MapTileFlags.BLOCK_MOVEMENT != 0) {
                        result.add(localX, localY, plane, MapTileFlags.BLOCK_MOVEMENT)
                    }
                    if (rules and MapTileFlags.LINK_BELOW != 0) {
                        result.add(localX, localY, plane, MapTileFlags.LINK_BELOW)
                    }
                    if (rules and MapTileFlags.REMOVE_ROOFS != 0) {
                        result.add(localX, localY, plane, MapTileFlags.REMOVE_ROOFS)
                    }
                }
            }
        }
    }

    private const val FILE_TERMINATOR = 0
}
