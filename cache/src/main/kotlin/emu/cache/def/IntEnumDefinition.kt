package emu.cache.def

/** One cache enum whose keys and values use integer-backed script types. */
data class IntEnumDefinition(
    val id: Int,
    val keyType: Char,
    val valueType: Char,
    val defaultValue: Int,
    val entries: List<Entry>,
) {
    /** One explicit key/value pair in cache order. */
    data class Entry(val key: Int, val value: Int)
}
