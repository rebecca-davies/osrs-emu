package emu.buffer

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

    fun writeShort(v: Int) {
        writeByte(v ushr 8)
        writeByte(v)
    }

    fun writeInt(v: Int) {
        writeByte(v ushr 24)
        writeByte(v ushr 16)
        writeByte(v ushr 8)
        writeByte(v)
    }

    fun writeLong(v: Long) {
        for (shift in 56 downTo 0 step 8) writeByte((v ushr shift).toInt())
    }

    fun writeBytes(b: ByteArray) {
        b.copyInto(array, pos)
        pos += b.size
    }

    fun readUMedium(): Int = (readUByte() shl 16) or (readUByte() shl 8) or readUByte()

    fun writeMedium(v: Int) {
        writeByte(v ushr 16)
        writeByte(v ushr 8)
        writeByte(v)
    }

    fun readCString(): String {
        val start = pos
        while (array[pos].toInt() != 0) pos++
        val s = String(array, start, pos - start, charset("windows-1252"))
        pos++ // skip the null terminator
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
