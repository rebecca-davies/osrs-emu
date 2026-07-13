package emu.protocol.osrs239.login

import emu.netcore.message.OutgoingMessage

// Reply to opcode 14: the 8-byte server session key the client stores as `cs.lv` and later echoes
// back inside the RSA login block. See rev239-login-facts.md §1-§2.
data class ServerSessionKey(val key: Long) : OutgoingMessage
