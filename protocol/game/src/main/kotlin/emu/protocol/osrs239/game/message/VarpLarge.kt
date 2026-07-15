package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Updates one client varp with a 32-bit value. */
data class VarpLarge(val id: Int, val value: Int) : OutgoingMessage
