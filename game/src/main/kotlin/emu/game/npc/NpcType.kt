package emu.game.npc

/** Cache-backed NPC fields required by authoritative world placement. */
data class NpcType(
    val id: Int,
    val name: String,
    val size: Int = 1,
) {
    init {
        require(id in 0 until (1 shl NPC_TYPE_BITS)) { "NPC type must fit the rev-239 information field" }
        require(name.isNotBlank()) { "NPC name must not be blank" }
        require(size in 1..0xFF) { "NPC size must fit an unsigned byte" }
    }

    private companion object {
        const val NPC_TYPE_BITS = 14
    }
}
