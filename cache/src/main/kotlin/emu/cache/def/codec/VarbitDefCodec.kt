package emu.cache.def.codec

import emu.buffer.JagexBuffer
import emu.cache.def.VarbitDefinition

/** Decodes rev-239's single-field varbit definition. */
object VarbitDefCodec {
    fun decode(id: Int, data: ByteArray): VarbitDefinition {
        val buf = JagexBuffer(data)
        var baseVar: Int? = null
        var bits: IntRange? = null
        while (true) {
            when (val opcode = buf.readUByte()) {
                0 -> break
                1 -> {
                    baseVar = buf.readUShort()
                    val leastBit = buf.readUByte()
                    bits = leastBit..buf.readUByte()
                }
                else -> error("Unrecognized varbit opcode $opcode for id $id")
            }
        }
        return VarbitDefinition(
            id = id,
            baseVar = requireNotNull(baseVar) { "varbit $id has no base varp" },
            bits = requireNotNull(bits) { "varbit $id has no bit range" },
        )
    }
}
