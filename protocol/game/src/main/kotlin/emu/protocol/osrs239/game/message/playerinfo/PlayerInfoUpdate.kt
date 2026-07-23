package emu.protocol.osrs239.game.message.playerinfo

import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHitmark
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import emu.protocol.osrs239.game.message.entity.hasUniqueSlots

/** Extended information appended for one active player-info bitcode. */
data class PlayerInfoUpdate(
    val appearance: PlayerAppearance? = null,
    val moveSpeed: Int? = null,
    val temporaryMoveSpeed: Int? = null,
    val publicChat: PlayerPublicChat? = null,
    val sequence: PlayerSequence? = null,
    val hitmarks: List<InfoHitmark> = emptyList(),
    val headbars: List<InfoHeadbar> = emptyList(),
    val spotAnimations: List<InfoSpotAnimation> = emptyList(),
) {
    init {
        require(
            appearance != null || moveSpeed != null || temporaryMoveSpeed != null ||
                publicChat != null || sequence != null || hitmarks.isNotEmpty() ||
                headbars.isNotEmpty() || spotAnimations.isNotEmpty(),
        ) {
            "player info update must contain at least one block"
        }
        require(moveSpeed == null || moveSpeed in VALID_MOVE_SPEEDS) { "invalid move speed $moveSpeed" }
        require(temporaryMoveSpeed == null || temporaryMoveSpeed in VALID_MOVE_SPEEDS) {
            "invalid temporary move speed $temporaryMoveSpeed"
        }
        require(hitmarks.size <= MAX_BLOCK_ENTRIES) { "too many player hitmarks" }
        require(headbars.size <= MAX_BLOCK_ENTRIES) { "too many player headbars" }
        require(spotAnimations.size <= MAX_BLOCK_ENTRIES) { "too many player spot animations" }
        require(spotAnimations.hasUniqueSlots()) {
            "player spot-animation slots must be unique"
        }
    }

    private companion object {
        val VALID_MOVE_SPEEDS = setOf(0, 1, 2, 127)
        const val MAX_BLOCK_ENTRIES = 0xFF
    }
}
