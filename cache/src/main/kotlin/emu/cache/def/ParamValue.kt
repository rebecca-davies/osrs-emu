package emu.cache.def

/**
 * Opcode-249 parameter value. Wire tag 1 selects string, 2 selects long, and all others select i32.
 */
sealed interface ParamValue {
    data class IntValue(val value: Int) : ParamValue
    data class StringValue(val value: String) : ParamValue
    data class LongValue(val value: Long) : ParamValue
}
