package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Controls whether ground-object interaction options are hidden. */
data class HideObjOps(val hidden: Boolean = false) : OutgoingMessage
