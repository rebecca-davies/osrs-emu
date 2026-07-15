package emu.protocol.osrs239.login.message

import emu.netcore.message.OutgoingMessage

/** Opcode-14 reply containing the 8-byte key echoed in the RSA login block. */
data class ServerSessionKey(val key: Long) : OutgoingMessage
