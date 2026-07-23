package emu.game.npc

/** Revision-neutral payload for one client-selected NPC menu operation. */
data class NpcOpInput(
    val index: Int,
    val option: Int,
    val subOption: Int = 0,
    val controlKey: Boolean = false,
) {
    init {
        require(index in 0..0xFFFF) { "NPC index must fit an unsigned short" }
        require(option in 1..5) { "NPC option must be in 1..5" }
        require(subOption in 0..0xFF) { "NPC sub-option must fit an unsigned byte" }
    }
}

/** Stable NPC identity and exact sub-operation supplied to authoritative content. */
data class NpcOpTarget(val uid: NpcUid, val subOption: Int) {
    init {
        require(subOption in 0..0xFF) { "NPC sub-option must fit an unsigned byte" }
    }
}
