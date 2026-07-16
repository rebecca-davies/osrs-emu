package emu.persistence.character.model

private val VARP_IDS = 0..0xFFFF

/** Dirty character state flushed at a write-behind save point. */
data class CharacterSave(
    val characterId: Long,
    val position: CharacterPosition,
    val playTimeSeconds: Long,
    val dirtyVarps: Map<Int, Int> = emptyMap(),
    val chatFilters: CharacterChatFilters = CharacterChatFilters(),
) {
    init {
        require(characterId > 0) { "character id must be positive" }
        require(playTimeSeconds >= 0) { "play time cannot be negative" }
        require(dirtyVarps.keys.all { it in VARP_IDS }) { "invalid varp id in save: ${dirtyVarps.keys}" }
    }
}
