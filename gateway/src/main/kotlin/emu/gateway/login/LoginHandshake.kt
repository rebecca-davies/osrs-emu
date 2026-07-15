package emu.gateway.login

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.login.codec.ServerSessionKeyEncoder
import emu.protocol.osrs239.login.message.ServerSessionKey
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.security.SecureRandom

/** Shared thread-safe session-key generator. */
private val secureRandom = SecureRandom()

/**
 * Handles opcode 14 (login init) after the pipeline's first-opcode dispatch: `LoginProt.INIT` has
 * no payload, so there is nothing to read here. Replies `[1 status byte = 0][8-byte server session
 * key, big-endian]`, which the client stores as `cs.lv` and echoes back inside the RSA login block.
 * This exchange occurs before ISAAC is initialized.
 *
 * The returned key must match the value echoed by the following login block.
 */
suspend fun performLoginInit(write: ByteWriteChannel, sessionKey: Long = secureRandom.nextLong()): Long {
    write.writeFully(ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(sessionKey)))
    write.flush()
    return sessionKey
}
