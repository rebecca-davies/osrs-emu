package emu.cache.def.codec.field

import emu.buffer.JagexBuffer
import emu.cache.def.ParamValue
import emu.cache.def.VarTransform

/** Encodes and decodes the insertion-ordered opcode-249 parameter map. */
internal object Params {
    fun decode(buf: JagexBuffer): LinkedHashMap<Int, ParamValue> {
        val size = buf.readUByte()
        val out = LinkedHashMap<Int, ParamValue>(size)
        repeat(size) {
            val type = buf.readUByte()
            val paramId = buf.readUMedium()
            val value =
                when (type) {
                    1 -> ParamValue.StringValue(buf.readCString())
                    2 -> ParamValue.LongValue(buf.readLong())
                    else -> ParamValue.IntValue(buf.readInt())
                }
            out[paramId] = value
        }
        return out
    }

    fun encode(writer: DefWriter, params: Map<Int, ParamValue>) {
        writer.writeByte(params.size)
        for ((paramId, param) in params) {
            when (param) {
                is ParamValue.StringValue -> {
                    writer.writeByte(1)
                    writer.writeMedium(paramId)
                    writer.writeString(param.value)
                }
                is ParamValue.LongValue -> {
                    writer.writeByte(2)
                    writer.writeMedium(paramId)
                    writer.writeLong(param.value)
                }
                is ParamValue.IntValue -> {
                    writer.writeByte(0)
                    writer.writeMedium(paramId)
                    writer.writeInt(param.value)
                }
            }
        }
    }
}

/** Emits paired find/replace colour or texture values under [opcode]. */
internal fun writePairs(
    writer: FragmentWriter,
    opcode: Int,
    find: List<Int>?,
    replace: List<Int>?,
) {
    if (find == null || replace == null) return
    writer.field(opcode) {
        writeByte(find.size)
        for (index in find.indices) {
            writeShort(find[index])
            writeShort(replace[index])
        }
    }
}

/** Emits a [VarTransform] as a base or extended opcode fragment. */
internal fun writeVarTransform(
    writer: FragmentWriter,
    transform: VarTransform,
    baseOpcode: Int,
    extendedOpcode: Int,
) {
    val length = transform.configChangeDest.size - 1
    if (transform.trailingVar != null) {
        writer.field(extendedOpcode) {
            writeShort(if (transform.varbitId == -1) 0xFFFF else transform.varbitId)
            writeShort(if (transform.varpId == -1) 0xFFFF else transform.varpId)
            writeShort(if (transform.trailingVar == -1) 0xFFFF else transform.trailingVar)
            writeByte(length)
            for (config in transform.configChangeDest) {
                writeShort(if (config == -1) 0xFFFF else config)
            }
        }
    } else {
        writer.field(baseOpcode) {
            writeShort(if (transform.varbitId == -1) 0xFFFF else transform.varbitId)
            writeShort(if (transform.varpId == -1) 0xFFFF else transform.varpId)
            writeByte(length)
            for (config in transform.configChangeDest) {
                writeShort(if (config == -1) 0xFFFF else config)
            }
        }
    }
}
