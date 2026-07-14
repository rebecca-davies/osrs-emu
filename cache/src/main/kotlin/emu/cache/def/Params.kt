package emu.cache.def

import emu.buffer.JagexBuffer
import emu.cache.def.codec.DefWriter

/**
 * A single `params` entry value (opcode 249). The wire type tag selects the payload: `1` = string,
 * `2` = long, anything else = i32 (recon doc §4, `InputStream.readParams`).
 */
sealed interface ParamValue {
    data class IntValue(val value: Int) : ParamValue
    data class StringValue(val value: String) : ParamValue
    data class LongValue(val value: Long) : ParamValue
}

/**
 * The shared `params` map (opcode 249): `paramId -> value`. Insertion order is preserved (the map
 * is emitted in iteration order) so a decoded-then-encoded def reproduces the original entry order.
 */
internal object Params {
    /** Reads `size u8` entries of `{ type u8; paramId u24; value }` into an insertion-ordered map. */
    fun decode(buf: JagexBuffer): LinkedHashMap<Int, ParamValue> {
        val size = buf.readUByte()
        val out = LinkedHashMap<Int, ParamValue>(size)
        repeat(size) {
            val type = buf.readUByte()
            val paramId = buf.readUMedium()
            val value = when (type) {
                1 -> ParamValue.StringValue(buf.readCString())
                2 -> ParamValue.LongValue(buf.readLong())
                else -> ParamValue.IntValue(buf.readInt())
            }
            out[paramId] = value
        }
        return out
    }

    /** Writes the opcode-249 payload: `size u8` then each `{ type u8; paramId u24; value }`. */
    fun encode(writer: DefWriter, params: Map<Int, ParamValue>) {
        writer.writeByte(params.size)
        for ((paramId, value) in params) {
            when (value) {
                is ParamValue.StringValue -> {
                    writer.writeByte(1)
                    writer.writeMedium(paramId)
                    writer.writeString(value.value)
                }
                is ParamValue.LongValue -> {
                    writer.writeByte(2)
                    writer.writeMedium(paramId)
                    writer.writeLong(value.value)
                }
                is ParamValue.IntValue -> {
                    writer.writeByte(0)
                    writer.writeMedium(paramId)
                    writer.writeInt(value.value)
                }
            }
        }
    }
}
