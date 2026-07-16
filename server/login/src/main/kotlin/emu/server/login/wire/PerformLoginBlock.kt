package emu.server.login.wire

import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.login.codec.LoginBlockParser
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import emu.server.session.authentication.AuthenticatedSession
import emu.server.session.authentication.AuthenticationDecision
import emu.server.session.authentication.IsaacBootstrap
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully

/**
 * Authenticates one framed login block and returns immutable game-handoff data.
 *
 * Successful login is acknowledged only after world entry, and credential-bearing buffers are
 * cleared before this function returns.
 */
suspend fun performLoginBlock(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    expectedServerKey: Long,
    rsaKeyPair: RsaKeyPair,
    authenticate: (String, CharArray) -> AuthenticationDecision,
): AuthenticatedSession? {
    val length = readU16(read)
    val payload = ByteArray(length)
    val parsedResult =
        try {
            read.readFully(payload)
            LoginBlockParser.parse(payload, rsaKeyPair.modulus, rsaKeyPair.privateExp)
        } finally {
            payload.fill(0)
        }

    return when (val result = parsedResult) {
        is LoginBlockParser.Result.BadMagic,
        is LoginBlockParser.Result.Malformed -> null
        is LoginBlockParser.Result.Ok -> {
            val parsed = result.parsed
            try {
                if (parsed.serverKey != expectedServerKey) return null
                val authentication = authenticate(parsed.username, parsed.password)
                if (authentication !is AuthenticationDecision.Authenticated) {
                    write.writeFully(
                        LoginResponseEncoder.encode(
                            NopStreamCipher,
                            LoginResponse(LoginResponse.INVALID_CREDENTIALS),
                        ),
                    )
                    write.flush()
                    return null
                }

                AuthenticatedSession(
                    account = authentication.account,
                    isaac = IsaacBootstrap.fromSeeds(parsed.seeds),
                )
            } finally {
                parsed.clearPassword()
            }
        }
    }
}

private suspend fun readU16(read: ByteReadChannel): Int {
    val hi = read.readByte().toInt() and 0xFF
    val lo = read.readByte().toInt() and 0xFF
    return (hi shl 8) or lo
}
