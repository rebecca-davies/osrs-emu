package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Sets the minimap state; zero is the normal visible in-world minimap. */
data class MinimapToggle(val state: Int = 0) : OutgoingMessage
