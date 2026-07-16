package emu.cache.def.codec.field

import emu.buffer.JagexBuffer

/** Reads a top-bit-selected i32 or u16 big smart with the u16 `32767` sentinel mapped to `-1`. */
internal fun JagexBuffer.readBigSmart2(): Int {
    val peek = array[pos].toInt()
    if (peek < 0) return readInt() and Int.MAX_VALUE
    val value = readUShort()
    return if (value == 32767) -1 else value
}

/** Reads a signed 16-bit big-endian value. */
internal fun JagexBuffer.readSignedShort(): Int = readUShort().toShort().toInt()

/** Reads a one- or two-byte unsigned short smart and subtracts one. */
internal fun JagexBuffer.readUnsignedShortSmartMinusOne(): Int {
    val peek = array[pos].toInt() and 0xFF
    return if (peek < 128) readUByte() - 1 else readUShort() - 0x8001
}
