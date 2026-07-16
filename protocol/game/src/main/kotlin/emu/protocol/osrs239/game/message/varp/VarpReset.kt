package emu.protocol.osrs239.game.message.varp

import emu.transport.message.OutgoingMessage

/** Resets the client's varp table before account-specific state is applied. */
data object VarpReset : OutgoingMessage
