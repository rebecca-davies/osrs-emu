package emu.protocol.osrs239.game.message.playerinfo

import emu.protocol.osrs239.game.message.chat.PlayerPublicChat

/** Extended information appended for one active player-info bitcode. */
data class PlayerInfoUpdate(
    val appearance: PlayerAppearance? = null,
    val moveSpeed: Int? = null,
    val temporaryMoveSpeed: Int? = null,
    val publicChat: PlayerPublicChat? = null,
    val sequence: PlayerSequence? = null,
) {
    init {
        require(
            appearance != null || moveSpeed != null || temporaryMoveSpeed != null ||
                publicChat != null || sequence != null,
        ) {
            "player info update must contain at least one block"
        }
        require(moveSpeed == null || moveSpeed in VALID_MOVE_SPEEDS) { "invalid move speed $moveSpeed" }
        require(temporaryMoveSpeed == null || temporaryMoveSpeed in VALID_MOVE_SPEEDS) {
            "invalid temporary move speed $temporaryMoveSpeed"
        }
    }

    private companion object {
        val VALID_MOVE_SPEEDS = setOf(0, 1, 2, 127)
    }
}
