package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.IntEnumDefinition

/** Decoder for cache enums with integer-backed keys and values. */
object IntEnumDefCodec {
    fun decode(id: Int, data: ByteArray): IntEnumDefinition {
        val buf = JagexBuffer(data)
        var keyType: Char? = null
        var valueType: Char? = null
        var defaultValue = 0
        var entries: List<IntEnumDefinition.Entry>? = null
        while (true) {
            when (val opcode = buf.readUByte()) {
                0 -> break
                1 -> keyType = buf.readUByte().toChar()
                2 -> valueType = buf.readUByte().toChar()
                3 -> buf.readCString()
                4 -> defaultValue = buf.readInt()
                5 -> skipStringEntries(buf)
                6 -> entries = readIntEntries(buf)
                7 -> skipLongEntries(buf)
                8 -> buf.readLong()
                else -> error("Unrecognized enum opcode $opcode for id $id")
            }
        }
        return IntEnumDefinition(
            id = id,
            keyType = requireNotNull(keyType) { "enum $id has no key type" },
            valueType = requireNotNull(valueType) { "enum $id has no value type" },
            defaultValue = defaultValue,
            entries = requireNotNull(entries) { "enum $id is not integer-valued" },
        )
    }

    private fun readIntEntries(buf: JagexBuffer): List<IntEnumDefinition.Entry> {
        val count = buf.readUShort()
        require(count <= MAX_ENTRIES) { "enum has more than $MAX_ENTRIES entries" }
        return List(count) { IntEnumDefinition.Entry(buf.readInt(), buf.readInt()) }
    }

    private fun skipStringEntries(buf: JagexBuffer) {
        val count = buf.readUShort()
        require(count <= MAX_ENTRIES) { "enum has more than $MAX_ENTRIES entries" }
        repeat(count) {
            buf.readInt()
            buf.readCString()
        }
    }

    private fun skipLongEntries(buf: JagexBuffer) {
        val count = buf.readUShort()
        require(count <= MAX_ENTRIES) { "enum has more than $MAX_ENTRIES entries" }
        repeat(count) {
            buf.readInt()
            buf.readLong()
        }
    }

    private const val MAX_ENTRIES = 8_192
}
