package emu.gateway.login

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.login.ServerSessionKey
import emu.protocol.osrs239.login.ServerSessionKeyEncoder
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.security.SecureRandom

/**
 * Handles opcode 14 (login init) after the pipeline's first-opcode dispatch: `LoginProt.INIT` has
 * no payload, so there is nothing to read here. Replies `[1 status byte = 0][8-byte server session
 * key, big-endian]`, which the client stores as `cs.lv` and echoes back inside the RSA login block
 * (see docs/superpowers/research/2026-07-14-rev239-login-facts.md §1). Pre-cipher, mirrors
 * `Js5Handshake.performHandshake`'s role for opcode 15.
 *
 * [sessionKey]'s generator is injectable (defaults to [SecureRandom]) so tests can pin a
 * deterministic key; the caller (`LoginHandler`) must remember the returned key to verify it is
 * echoed back correctly in the login block.
 */
suspend fun performLoginInit(write: ByteWriteChannel, sessionKey: Long = SecureRandom().nextLong()): Long {
    write.writeFully(ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(sessionKey)))
    write.flush()
    return sessionKey
}
