package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Restores the normal player-following camera mode. */
data object CamReset : OutgoingMessage
