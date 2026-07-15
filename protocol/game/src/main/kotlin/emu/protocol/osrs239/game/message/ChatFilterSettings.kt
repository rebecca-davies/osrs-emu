package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Initializes public and trade chat filters. */
data class ChatFilterSettings(val publicFilter: Int = 0, val tradeFilter: Int = 0) : OutgoingMessage
