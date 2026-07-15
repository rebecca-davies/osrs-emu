package emu.cache.def

/**
 * Entity-operation menu shared by objects, NPCs, and items.
 * Only what the opcodes actually wrote is captured — no post-load defaults (e.g. an item's implicit
 * "Take" op) are materialised here, so a decode/encode round-trip reproduces the source bytes.
 *
 * The concrete opcode numbers differ per definition type (objects use 30-34/100-102, items
 * 30-34/200-202, npcs 30-34/251-253); [emu.cache.def.codec.EntityOpsCodec] maps this structure onto
 * a given opcode set.
 */
data class EntityOps(
    /** Plain menu operations by slot index; hidden operations are omitted. */
    val ops: Map<Int, String> = emptyMap(),
    val subOps: List<SubOp> = emptyList(),
    val conditionalOps: List<ConditionalOp> = emptyList(),
    val conditionalSubOps: List<ConditionalSubOp> = emptyList(),
) {
    fun isEmpty(): Boolean =
        ops.isEmpty() && subOps.isEmpty() && conditionalOps.isEmpty() && conditionalSubOps.isEmpty()

    data class SubOp(val index: Int, val subId: Int, val text: String)
    data class ConditionalOp(
        val index: Int,
        val varp: Int,
        val varb: Int,
        val min: Int,
        val max: Int,
        val text: String,
    )
    data class ConditionalSubOp(
        val index: Int,
        val subId: Int,
        val varp: Int,
        val varb: Int,
        val min: Int,
        val max: Int,
        val text: String,
    )
}
