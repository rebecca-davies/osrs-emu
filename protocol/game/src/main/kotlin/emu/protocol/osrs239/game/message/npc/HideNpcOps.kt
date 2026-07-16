package emu.protocol.osrs239.game.message.npc

import emu.transport.message.OutgoingMessage

/** Controls whether NPC interaction options are hidden. */
data class HideNpcOps(val hidden: Boolean = false) : OutgoingMessage
