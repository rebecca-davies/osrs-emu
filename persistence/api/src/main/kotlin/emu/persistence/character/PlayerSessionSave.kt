package emu.persistence.character

private val VARP_IDS = 0..0xFFFF

/** Dirty character state flushed at a write-behind save point. */
data class PlayerSessionSave(
    val playerId: Long,
    val position: PlayerPosition,
    val playedSeconds: Long,
    val dirtyVarps: Map<Int, Int> = emptyMap(),
) {
    init {
        require(playerId > 0) { "player id must be positive" }
        require(playedSeconds >= 0) { "played time cannot be negative" }
        require(dirtyVarps.keys.all { it in VARP_IDS }) { "invalid varp id in save: ${dirtyVarps.keys}" }
    }
}
