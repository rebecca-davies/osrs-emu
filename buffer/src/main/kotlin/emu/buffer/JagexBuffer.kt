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

    companion object {
        fun alloc(size: Int): JagexBuffer = JagexBuffer(ByteArray(size))
    }
}
