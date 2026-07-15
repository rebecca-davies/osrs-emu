package emu.persistence.character

private val VARP_IDS = 0..0xFFFF

/** Dirty character state flushed at a write-behind save point. */
data class PlayerSessionSave(
    val playerId: Long,
    val position: PlayerPosition,
    val playTimeSeconds: Long,
    val dirtyVarps: Map<Int, Int> = emptyMap(),
    val chatFilters: PlayerChatFiltersRecord = PlayerChatFiltersRecord(),
) {
    init {
        require(playerId > 0) { "player id must be positive" }
        require(playTimeSeconds >= 0) { "play time cannot be negative" }
        require(dirtyVarps.keys.all { it in VARP_IDS }) { "invalid varp id in save: ${dirtyVarps.keys}" }
    }
}
