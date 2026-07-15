package emu.protocol.osrs239.game.message

import emu.netcore.message.IncomingMessage

/** Client-selected public/private/trade visibility modes. */
data class SetChatFilterSettings(
    val publicFilter: Int,
    val privateFilter: Int,
    val tradeFilter: Int,
) : IncomingMessage
