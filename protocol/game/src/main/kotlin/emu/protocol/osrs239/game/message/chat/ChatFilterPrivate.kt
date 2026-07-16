package emu.protocol.osrs239.game.message.chat

import emu.transport.message.OutgoingMessage

/** Updates the private-chat filter, which has its own rev-239 server packet. */
data class ChatFilterPrivate(val privateFilter: Int) : OutgoingMessage
