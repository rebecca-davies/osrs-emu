package emu.buffer

/** Mutable byte-array cursor implementing Jagex primitive encodings. */
class JagexBuffer(val array: ByteArray, var pos: Int = 0) {

    fun readableBytes(): Int = array.size - pos

    fun readUByte(): Int = array[pos++].toInt() and 0xFF
    fun readByte(): Int = array[pos++].toInt()

    fun readUShort(): Int = (readUByte() shl 8) or readUByte()

    fun readInt(): Int =
        (readUByte() shl 24) or (readUByte() shl 16) or (readUByte() shl 8) or readUByte()

    fun readLong(): Long {
        var v = 0L
        repeat(8) { v = (v shl 8) or readUByte().toLong() }
        return v
    }

    fun readBytes(n: Int): ByteArray {
        val out = array.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun writeByte(v: Int) { array[pos++] = v.toByte() }

    /** Jagex `p1Alt1`: writes `value + 128` modulo 256. */
    fun writeByteAlt1(v: Int) = writeByte(v + 128)

    /** Jagex `p1Alt2`: writes `-value` modulo 256. */
    fun writeByteAlt2(v: Int) = writeByte(-v)

    /** Jagex `p1Alt3`: writes `128 - value` modulo 256. */
    fun writeByteAlt3(v: Int) = writeByte(128 - v)

    fun writeShort(v: Int) {
        writeByte(v ushr 8)
        writeByte(v)
    }

    /** Jagex `p2Alt1`: little-endian u16. */
    fun writeShortAlt1(v: Int) {
        writeByte(v)
        writeByte(v ushr 8)
    }

    /** Jagex `p2Alt2`: big-endian u16 with 128 added to the low byte. */
    fun writeShortAlt2(v: Int) {
        writeByte(v ushr 8)
        writeByte(v + 128)
    }

    /** Jagex `p2Alt3`: little-endian u16 with 128 added to the low byte. */
    fun writeShortAlt3(v: Int) {
        writeByte(v + 128)
        writeByte(v ushr 8)
    }

    fun writeInt(v: Int) {
        writeByte(v ushr 24)
        writeByte(v ushr 16)
        writeByte(v ushr 8)
        writeByte(v)
    }

    /** Jagex `p4Alt1`: little-endian i32. */
    fun writeIntAlt1(v: Int) {
        writeByte(v)
        writeByte(v ushr 8)
        writeByte(v ushr 16)
        writeByte(v ushr 24)
    }

    /** Jagex `p4Alt2`: middle-endian i32 byte order 1,0,3,2. */
    fun writeIntAlt2(v: Int) {
        writeByte(v ushr 8)
        writeByte(v)
        writeByte(v ushr 24)
        writeByte(v ushr 16)
    }

    /** Jagex `p4Alt3`: inverse-middle-endian i32 byte order 2,3,0,1. */
    fun writeIntAlt3(v: Int) {
        writeByte(v ushr 16)
        writeByte(v ushr 24)
        writeByte(v)
        writeByte(v ushr 8)
    }

    fun writeLong(v: Long) {
        for (shift in 56 downTo 0 step 8) writeByte((v ushr shift).toInt())
    }

    fun writeBytes(b: ByteArray) {
        b.copyInto(array, pos)
        pos += b.size
    }

    /**
     * Jagex `pSmart1or2`: writes `0..0x7FFF` as a single byte when it fits in 7 bits, otherwise as a
     * big-endian u16 with the high bit set (`value + 0x8000`). The reader decides the width by
     * peeking whether the first byte's top bit is set.
     */
    fun writeSmart1or2(v: Int) {
        require(v in 0..0x7FFF) { "smart1or2 value out of range: $v" }
        if (v < 0x80) writeByte(v) else writeShort(v + 0x8000)
    }

    fun readUMedium(): Int = (readUByte() shl 16) or (readUByte() shl 8) or readUByte()

    fun writeMedium(v: Int) {
        writeByte(v ushr 16)
        writeByte(v ushr 8)
        writeByte(v)
    }

    /**
     * Reads a CP-1252 C-string up to (and consuming) a `0x00` terminator. If no terminator is
     * found before the end of the backing array, the string runs to the end of the buffer and the
     * cursor stops there — bounds-safe, so a login block whose password fills the RSA plaintext
     * exactly (no room for the terminator) yields the string instead of an out-of-bounds read.
     */
    fun readCString(): String {
        val start = pos
        while (pos < array.size && array[pos].toInt() != 0) pos++
        val s = String(array, start, pos - start, charset("windows-1252"))
        if (pos < array.size) pos++ // consume the null terminator when one is present
        return s
    }

    fun writeCString(s: String) {
        val bytes = s.toByteArray(charset("windows-1252"))
        writeBytes(bytes)
        writeByte(0)
    }

    companion object {
        fun alloc(size: Int): JagexBuffer = JagexBuffer(ByteArray(size))
    }
}
