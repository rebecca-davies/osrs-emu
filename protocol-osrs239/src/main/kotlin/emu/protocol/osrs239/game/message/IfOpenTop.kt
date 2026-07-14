package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Opens a top-level interface, normally rev-239's game-frame interface 165. */
data class IfOpenTop(val interfaceId: Int) : OutgoingMessage
