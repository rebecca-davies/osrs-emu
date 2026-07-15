package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Resets the client's varp table before account-specific state is applied. */
data object VarpReset : OutgoingMessage
