package emu.protocol.osrs239.game.message.zone

import emu.transport.message.OutgoingMessage

/** Controls whether map-location interaction options are hidden. */
data class HideLocOps(val hidden: Boolean = false) : OutgoingMessage
