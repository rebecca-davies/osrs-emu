package emu.server.login.wire

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.login.codec.ServerSessionKeyEncoder
import emu.protocol.osrs239.login.message.ServerSessionKey
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.security.SecureRandom

/** Process-wide source for independent login session keys. */
private val secureRandom = SecureRandom()

/** Writes login-init success and a server key that the following login block must echo. */
suspend fun performLoginInit(write: ByteWriteChannel, sessionKey: Long = secureRandom.nextLong()): Long {
    write.writeFully(ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(sessionKey)))
    write.flush()
    return sessionKey
}
