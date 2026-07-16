package emu.protocol.osrs239.game.message.varp

import emu.transport.message.OutgoingMessage

/** Updates one client varp whose signed value fits in a byte. */
data class VarpSmall(val id: Int, val value: Int) : OutgoingMessage
