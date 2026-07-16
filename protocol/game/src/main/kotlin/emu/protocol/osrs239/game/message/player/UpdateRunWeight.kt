package emu.protocol.osrs239.game.message.player

import emu.transport.message.OutgoingMessage

/** Updates carried weight in the client's signed fixed-point representation. */
data class UpdateRunWeight(val weight: Int = 0) : OutgoingMessage
