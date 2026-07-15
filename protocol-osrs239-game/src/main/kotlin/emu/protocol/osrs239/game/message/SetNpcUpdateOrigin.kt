package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/**
 * SET_NPC_UPDATE_ORIGIN (opcode 116) — the base tile the following NPC_INFO bit stream is encoded
 * relative to. [originX]/[originZ] are the local player's tile minus the current build-area base,
 * each a u8 within the 13x13 scene.
 */
data class SetNpcUpdateOrigin(val originX: Int, val originZ: Int) : OutgoingMessage
