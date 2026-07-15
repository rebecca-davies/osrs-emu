package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Empty opcode-83 marker that commits the cycle's accumulated client updates. */
data object ServerTickEnd : OutgoingMessage
