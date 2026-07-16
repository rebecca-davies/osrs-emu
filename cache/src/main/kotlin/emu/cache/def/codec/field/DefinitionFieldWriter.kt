package emu.cache.def.codec.field

import java.io.ByteArrayOutputStream

/** Growable big-endian writer for definition fields and smart integers. */
internal class DefWriter {
    private val out = ByteArrayOutputStream()

    fun writeByte(value: Int) {
        out.write(value and 0xFF)
    }

    fun writeShort(value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun writeMedium(value: Int) {
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun writeInt(value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun writeLong(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            out.write(((value ushr shift) and 0xFF).toInt())
        }
    }

    fun writeString(value: String) {
        out.writeBytes(value.toByteArray(charset("windows-1252")))
        out.write(0)
    }

    fun writeBigSmart2(value: Int) {
        when {
            value == -1 -> writeShort(32767)
            value < 32768 -> writeShort(value)
            else -> writeInt(value or (1 shl 31))
        }
    }

    fun writeUnsignedShortSmartMinusOne(value: Int) {
        if (value in -1..126) {
            writeByte(value + 1)
        } else {
            writeShort(value + 0x8001)
        }
    }

    fun writeBytes(bytes: ByteArray) {
        out.writeBytes(bytes)
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Emits definition field fragments in ascending opcode order followed by opcode zero. */
internal class FragmentWriter {
    private val fragments = mutableListOf<Pair<Int, ByteArray>>()

    fun field(opcode: Int, block: DefWriter.() -> Unit) {
        val writer = DefWriter()
        writer.block()
        fragments.add(opcode to writer.toByteArray())
    }

    fun flag(opcode: Int, present: Boolean) {
        if (present) fragments.add(opcode to EMPTY)
    }

    fun build(): ByteArray {
        val writer = DefWriter()
        for ((opcode, payload) in fragments.sortedBy { it.first }) {
            writer.writeByte(opcode)
            writer.writeBytes(payload)
        }
        writer.writeByte(0)
        return writer.toByteArray()
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}
