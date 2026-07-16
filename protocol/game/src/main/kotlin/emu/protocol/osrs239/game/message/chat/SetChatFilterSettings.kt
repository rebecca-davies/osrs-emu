package emu.protocol.osrs239.game.message.chat

import emu.transport.message.IncomingMessage

/** Client-selected public/private/trade visibility modes. */
data class SetChatFilterSettings(
    val publicFilter: Int,
    val privateFilter: Int,
    val tradeFilter: Int,
) : IncomingMessage
