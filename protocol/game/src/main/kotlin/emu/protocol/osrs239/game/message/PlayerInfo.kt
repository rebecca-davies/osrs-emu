package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Four-section player information update for one observing client. */
data class PlayerInfo(
    val sections: PlayerInfoSections,
) : OutgoingMessage {
    /** Builds the single-local-player form used during login and protocol-focused callers. */
    constructor(
        appearance: PlayerAppearance? = null,
        movement: PlayerMovement? = null,
        moveSpeed: Int? = null,
        temporaryMoveSpeed: Int? = null,
        publicChat: PlayerPublicChat? = null,
    ) : this(
        localSections(appearance, movement, moveSpeed, temporaryMoveSpeed, publicChat),
    )

    private companion object {
        const val OTHER_PLAYER_SLOTS = 2_046

        fun localSections(
            appearance: PlayerAppearance?,
            movement: PlayerMovement?,
            moveSpeed: Int?,
            temporaryMoveSpeed: Int?,
            publicChat: PlayerPublicChat?,
        ): PlayerInfoSections {
            val update =
                if (appearance != null || moveSpeed != null || temporaryMoveSpeed != null || publicChat != null) {
                    PlayerInfoUpdate(appearance, moveSpeed, temporaryMoveSpeed, publicChat)
                } else {
                    null
                }
            val local =
                if (movement == null && update == null) {
                    PlayerInfoBitCode.Skip(players = 1)
                } else {
                    PlayerInfoBitCode.HighResolution(movement, update)
                }
            return PlayerInfoSections(
                highResolutionActive = listOf(local),
                lowResolutionActive = listOf(PlayerInfoBitCode.Skip(OTHER_PLAYER_SLOTS)),
            )
        }
    }
}
