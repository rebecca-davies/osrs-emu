package emu.game.content.areas.inferno

/** Ordered, bounded set of NPC types exposed by the Inferno free-mode editor. */
class InfernoEditorRoster(entries: List<InfernoEditorNpc>) {
    val entries: List<InfernoEditorNpc> = entries.toList()

    init {
        require(this.entries.isNotEmpty()) { "Inferno editor roster cannot be empty" }
        require(this.entries.size <= MAX_ENTRIES) {
            "Inferno editor roster cannot exceed $MAX_ENTRIES entries"
        }
        require(this.entries.distinctBy(InfernoEditorNpc::type).size == this.entries.size) {
            "Inferno editor NPC types must be unique"
        }
        require(this.entries.distinctBy { it.displayName.lowercase() }.size == this.entries.size) {
            "Inferno editor NPC display names must be unique"
        }
    }

    operator fun get(index: Int): InfernoEditorNpc? = entries.getOrNull(index)

    companion object {
        /** Maximum number of NPC cards supported by the bundled rev-239 editor layout. */
        const val MAX_ENTRIES = 8
    }
}

/** Exact cache type and concise name shown by one Inferno editor card. */
data class InfernoEditorNpc(val type: Int, val displayName: String) {
    init {
        require(type in NPC_TYPE_RANGE) { "Inferno editor NPC type must fit 14 bits" }
        require(displayName.isNotBlank()) { "Inferno editor NPC display name cannot be blank" }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) {
            "Inferno editor NPC display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
        }
    }

    private companion object {
        val NPC_TYPE_RANGE = 0 until (1 shl 14)
        const val MAX_DISPLAY_NAME_LENGTH = 24
    }
}
