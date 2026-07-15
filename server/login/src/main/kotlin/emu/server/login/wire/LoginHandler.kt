package emu.server.login.wire

import emu.protocol.osrs239.login.codec.LoginBlockParser

import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.server.session.AuthenticationDecision
import emu.server.session.AuthenticatedSession
import emu.server.session.ConnectionBootstrap
import emu.server.session.isaacBootstrap
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully

private val logger = KotlinLogging.logger {}

/**
 * Authenticates one u16-framed login block after the login opcode has been consumed.
 *
 * The echoed server key must match [expectedServerKey]. Invalid credentials receive response code
 * 3; successful authentication returns immutable identity and ISAAC bootstrap data without writing
 * login success. The coordinator writes success only after game admission. Packet and password
 * buffers are cleared before return, and credential-bearing bytes are never logged.
 *
 * [reconnect] selects the op-18 authentication-token layout and suppresses the fresh-login account
 * trailer when the login service later completes the request.
 */
suspend fun performLoginBlock(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    expectedServerKey: Long,
    rsaKeyPair: RsaKeyPair,
    reconnect: Boolean = false,
    authenticate: (String, CharArray) -> AuthenticationDecision,
): AuthenticatedSession? {
    val length = readU16(read)
    logger.debug { "login block: framed u16 length=$length; reading that many payload bytes" }
    val payload = ByteArray(length)
    val payloadSize = payload.size
    val parsedResult =
        try {
            read.readFully(payload)
            LoginBlockParser.parse(
                payload,
                rsaKeyPair.modulus,
                rsaKeyPair.privateExp,
                reconnect = reconnect,
            )
        } finally {
            payload.fill(0)
        }

    return when (val result = parsedResult) {
        is LoginBlockParser.Result.BadMagic -> {
            logger.warn {
                "login block rejected: RSA decrypt did not yield magic byte 1 (got ${result.magicByte}) " +
                    "using cleartext-header offset ${result.headerSizeUsed} " +
                    "(LoginBlockParser.CLEARTEXT_HEADER_SIZE) — adjust that constant against the real " +
                    "client's captured bytes"
            }
            null
        }
        is LoginBlockParser.Result.Malformed -> {
            logger.warn { "login block malformed ($payloadSize bytes): ${result.reason}" }
            null
        }
        is LoginBlockParser.Result.Ok -> {
            logger.debug { "login block: $payloadSize bytes, decrypt OK" }
            val parsed = result.parsed
            try {
                if (parsed.serverKey != expectedServerKey) {
                    logger.warn { "login block echoed a different server key; rejecting" }
                    return null
                }
                val authentication = authenticate(parsed.username, parsed.password)
                if (authentication !is AuthenticationDecision.Authenticated) {
                    write.writeFully(
                        LoginResponseEncoder.encode(
                            NopStreamCipher,
                            LoginResponse(LoginResponse.INVALID_CREDENTIALS),
                        ),
                    )
                    write.flush()
                    logger.info { "login rejected: invalid credentials" }
                    return null
                }

                AuthenticatedSession(
                    ConnectionBootstrap(
                        isaac = isaacBootstrap(parsed.seeds),
                        principal = authentication.principal,
                    ),
                    reconnect,
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
