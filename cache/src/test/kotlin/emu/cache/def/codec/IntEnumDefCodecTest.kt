package emu.cache.def.codec

import emu.cache.def.IntEnumDefinition
import emu.cache.def.codec.field.DefWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IntEnumDefCodecTest {
    @Test
    fun `decodes ordered integer named-object entries`() {
        val data =
            DefWriter().apply {
                writeByte(1)
                writeByte('i'.code)
                writeByte(2)
                writeByte('O'.code)
                writeByte(3)
                writeString("unused")
                writeByte(4)
                writeInt(-1)
                writeByte(6)
                writeShort(2)
                writeInt(0)
                writeInt(4_151)
                writeInt(1)
                writeInt(20_997)
                writeByte(7)
                writeShort(1)
                writeInt(2)
                writeLong(3L)
                writeByte(8)
                writeLong(-1L)
                writeByte(0)
            }.toByteArray()

        val definition = IntEnumDefCodec.decode(1_124, data)

        assertEquals('i', definition.keyType)
        assertEquals('O', definition.valueType)
        assertEquals(-1, definition.defaultValue)
        assertEquals(
            listOf(
                IntEnumDefinition.Entry(0, 4_151),
                IntEnumDefinition.Entry(1, 20_997),
            ),
            definition.entries,
        )
    }

    @Test
    fun `rejects an unbounded or non-integer enum`() {
        val oversized =
            DefWriter().apply {
                writeByte(1)
                writeByte('i'.code)
                writeByte(2)
                writeByte('O'.code)
                writeByte(6)
                writeShort(8_193)
            }.toByteArray()
        assertFailsWith<IllegalArgumentException> {
            IntEnumDefCodec.decode(1, oversized)
        }

        val stringOnly =
            DefWriter().apply {
                writeByte(1)
                writeByte('i'.code)
                writeByte(2)
                writeByte('s'.code)
                writeByte(5)
                writeShort(0)
                writeByte(0)
            }.toByteArray()
        assertFailsWith<IllegalArgumentException> {
            IntEnumDefCodec.decode(2, stringOnly)
        }
    }
}
