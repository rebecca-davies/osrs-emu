package emu.protocol.osrs239.game.message.npc

import emu.transport.message.IncomingMessage

/** Second NPC menu operation selected for one client-local NPC index. */
data class OpNpc2(
    val index: Int,
    val subOption: Int,
    val controlKey: Boolean,
) : IncomingMessage
