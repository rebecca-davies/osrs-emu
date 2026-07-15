package emu.cache.def.codec

import emu.buffer.JagexBuffer
import java.io.ByteArrayOutputStream

/**
 * A growable, big-endian byte writer for definition encoders. [emu.buffer.JagexBuffer] requires a
 * pre-sized backing array, while a definition's encoded length is known only after its fields are
 * emitted. This writer also provides the definition format's smart integers.
 */
internal class DefWriter {
    private val out = ByteArrayOutputStream()

    fun writeByte(v: Int) { out.write(v and 0xFF) }

    fun writeShort(v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeMedium(v: Int) {
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeInt(v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeLong(v: Long) {
        for (shift in 56 downTo 0 step 8) out.write(((v ushr shift) and 0xFF).toInt())
    }

    /** Writes a CP-1252 C-string terminated by `0x00` — the inverse of [JagexBuffer.readCString]. */
    fun writeString(s: String) {
        out.writeBytes(s.toByteArray(charset("windows-1252")))
        out.write(0)
    }

    /** Inverse of [readBigSmart2]: `-1` maps to the u16 sentinel `32767`, `< 32768` to a u16, else a top-bit-set i32. */
    fun writeBigSmart2(value: Int) {
        when {
            value == -1 -> writeShort(32767)
            value < 32768 -> writeShort(value)
            else -> writeInt(value or (1 shl 31))
        }
    }

    /** Inverse of [readUnsignedShortSmartMinusOne]: values `-1..126` as a single byte `v+1`, else a u16 `v+0x8001`. */
    fun writeUnsignedShortSmartMinusOne(value: Int) {
        if (value in -1..126) {
            writeByte(value + 1)
        } else {
            writeShort(value + 0x8001)
        }
    }

    fun writeBytes(b: ByteArray) { out.writeBytes(b) }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/**
 * Accumulates a definition's fields as `(opcode, payload)` fragments, then emits them in ascending
 * opcode order (a stable sort preserves insertion order among equal opcodes) terminated by opcode 0.
 *
 * Fragment sorting keeps output ascending when a field's opcode depends on its value.
 */
internal class FragmentWriter {
    private val fragments = mutableListOf<Pair<Int, ByteArray>>()

    /** Records a field: its [opcode] plus a payload built by [block]. */
    fun field(opcode: Int, block: DefWriter.() -> Unit) {
        val w = DefWriter()
        w.block()
        fragments.add(opcode to w.toByteArray())
    }

    /** Records a no-payload flag opcode iff [present]. */
    fun flag(opcode: Int, present: Boolean) {
        if (present) fragments.add(opcode to EMPTY)
    }

    fun build(): ByteArray {
        val out = DefWriter()
        for ((opcode, payload) in fragments.sortedBy { it.first }) {
            out.writeByte(opcode)
            out.writeBytes(payload)
        }
        out.writeByte(0)
        return out.toByteArray()
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}

/** Reads a signed 16-bit big-endian value (the buffer only exposes the unsigned form). */
internal fun JagexBuffer.readSignedShort(): Int = readUShort().toShort().toInt()

/**
 * Peek-based i32/u16 big smart with a `-1` sentinel:
 * a set top bit means a masked i32; otherwise a u16 where `32767` means `-1`.
 */
internal fun JagexBuffer.readBigSmart2(): Int {
    val peek = array[pos].toInt()
    if (peek < 0) return readInt() and Int.MAX_VALUE
    val value = readUShort()
    return if (value == 32767) -1 else value
}

/**
 * Peek-based short-smart minus one:
 * a leading byte `< 128` is `u8 - 1`; otherwise `u16 - 0x8001`.
 */
internal fun JagexBuffer.readUnsignedShortSmartMinusOne(): Int {
    val peek = array[pos].toInt() and 0xFF
    return if (peek < 128) readUByte() - 1 else readUShort() - 0x8001
}
