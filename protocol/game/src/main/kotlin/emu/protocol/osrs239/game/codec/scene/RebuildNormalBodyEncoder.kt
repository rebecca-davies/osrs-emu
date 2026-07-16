package emu.protocol.osrs239.game.codec.scene

import emu.buffer.JagexBuffer

/** Encodes the six-byte body shared by login and subsequent normal scene rebuilds. */
internal object RebuildNormalBodyEncoder {
    fun encode(centreZoneX: Int, centreZoneY: Int, worldArea: Int): ByteArray {
        require(centreZoneX in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt())
        require(centreZoneY in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt())
        require(worldArea in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt())
        val out = JagexBuffer.alloc(BODY_SIZE)
        out.writeShort(worldArea)
        writeZoneCoord(out, centreZoneY)
        writeZoneCoord(out, centreZoneX)
        return out.array
    }

    /** Writes rev-239's `g2Alt2`: big-endian with 128 added to the low byte. */
    private fun writeZoneCoord(out: JagexBuffer, value: Int) {
        out.writeByte(value shr 8)
        out.writeByte(value + 128)
    }

    private const val BODY_SIZE = 6
}
