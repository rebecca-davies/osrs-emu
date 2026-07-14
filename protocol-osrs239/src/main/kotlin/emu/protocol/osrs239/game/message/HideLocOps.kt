package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Controls whether map-location interaction options are hidden. */
data class HideLocOps(val hidden: Boolean = false) : OutgoingMessage
