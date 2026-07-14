package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * SET_NPC_UPDATE_ORIGIN (opcode 116) — the base tile the following NPC_INFO bit stream is encoded
 * relative to. [originX]/[originZ] are the local player's tile minus the current build-area base
 * (`baseX = (zoneX - 6) * 8`), each a u8 (0..104 within the 13x13 scene). rsmod sends this every
 * cycle after PLAYER_INFO; without it the client's npc-info coordinate base is never set.
 */
data class SetNpcUpdateOrigin(val originX: Int, val originZ: Int) : OutgoingMessage
