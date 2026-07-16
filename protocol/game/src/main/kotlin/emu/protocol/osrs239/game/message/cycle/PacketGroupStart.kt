package emu.protocol.osrs239.game.message.cycle

import emu.transport.message.OutgoingMessage

/** Declares the number of complete packet-frame bytes in the atomic world-update group that follows. */
data class PacketGroupStart(val length: Int) : OutgoingMessage
