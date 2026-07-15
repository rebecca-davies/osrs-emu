package emu.game.chat

/** Complete client chat visibility selection validated on the game cycle. */
data class ChatFilterInput(
    val publicFilter: Int,
    val privateFilter: Int,
    val tradeFilter: Int,
) : ChatInput
