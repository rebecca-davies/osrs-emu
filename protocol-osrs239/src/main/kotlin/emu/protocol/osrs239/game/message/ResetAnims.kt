package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Clears transient entity animation state during login initialization. */
data object ResetAnims : OutgoingMessage
