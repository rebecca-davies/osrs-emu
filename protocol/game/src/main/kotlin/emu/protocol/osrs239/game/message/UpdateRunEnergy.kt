package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Updates run energy in hundredths of a percent; 10,000 is full energy. */
data class UpdateRunEnergy(val energy: Int = 10_000) : OutgoingMessage
