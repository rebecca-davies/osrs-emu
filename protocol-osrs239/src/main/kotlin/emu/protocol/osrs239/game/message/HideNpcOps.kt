package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Controls whether NPC interaction options are hidden. */
data class HideNpcOps(val hidden: Boolean = false) : OutgoingMessage
