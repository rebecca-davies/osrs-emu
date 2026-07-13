package emu.protocol.osrs239.login

import emu.netcore.message.IncomingMessage

/**
 * Opcode 16 (new login) or 18 (reconnect): a u16-length-prefixed (VAR_SHORT) payload. The RSA
 * block, plaintext layout, and XTEA tail inside [payload] are parsed by `LoginHandler`; this
 * message is just the framed byte carrier decoded by the pipeline.
 * See docs/superpowers/research/2026-07-14-rev239-login-facts.md §1-§2.
 */
data class LoginBlock(val opcode: Int, val payload: ByteArray) : IncomingMessage
