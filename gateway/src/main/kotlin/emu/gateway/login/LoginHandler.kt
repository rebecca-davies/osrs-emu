package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.persistence.AuthenticationResult
import emu.persistence.PlayerRecord
import emu.persistence.PlayerRank
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/** Authenticated account state and ISAAC ciphers handed to the game stage. */
data class AuthenticatedGameLogin(
    val inbound: IsaacCipher,
    val outbound: IsaacCipher,
    val player: PlayerRecord,
)

/**
 * The local player's index into the client's 2048-slot player array (`client.di`,
 * docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §2/§4a). This is the one
 * load-bearing field in the login-info trailer ([LOGIN_SUCCESS_TRAILER]) — the game stage's
 * initial `RebuildNormal`/`PlayerInfo` packets describe this same local player. Milestone-3 has no
 * persisted account/slot table, so every connection is simply assigned index 1 (a valid non-zero
 * slot; index 0 is conventionally reserved).
 */
val LOCAL_PLAYER_INDEX: Int = System.getenv("EMU_LOCAL_INDEX")?.toIntOrNull() ?: 1

/**
 * Value the client requires in the byte after response code 2. This is not the number of account
 * info bytes which follow: it is the 34-byte account-info payload plus the first game's
 * `[opcode][u16 length]` header. The login state waits for all 37 bytes before it starts parsing.
 */
private const val LOGIN_INFO_SPAN = 37

/** Bytes actually consumed as account info before the login state reads the first game header. */
private const val LOGIN_INFO_PAYLOAD_LENGTH = 34

/** Byte offset of the 2-byte `di` field within the login-info block (ingame-facts.md §2). */
private const val DI_OFFSET = 7

/**
 * The bytes written right after the response-2 code byte.
 *
 * Task 7 (empirical, against the real rev-239 client, decompiled `client.java`): on response code 2
 * the client (non-BETA world => state `cd.az`) reads ONE span byte that must equal `jz.ax.av`
 * (37); a mismatch calls `xz.ai(byte)` and bounces to the login screen. It then (state `cd.am`)
 * waits until 37 bytes are available, but consumes only this 34-byte login-info payload:
 * ```
 * [1] authenticator flag  [4] encrypted authenticator code
 * [1] staff-mod level  [1] player-mod flag  [2] local player index  [1] members flag
 * [8] account hash  [8] user id  [8] user hash              (= 34 parsed bytes)
 * ```
 * The final three bytes in the advertised span are the immediately following first game packet's
 * smart opcode (one byte for REBUILD_NORMAL) and u16 body length. Adding three account-info pad
 * bytes here makes the login state parse those zeros as the first packet header and advances the
 * inbound ISAAC stream before the real rebuild.
 * Every field zero-fills for milestone-3 (auto-accept, no persisted account) except `di`, which
 * carries [LOCAL_PLAYER_INDEX] — the client needs this to index its own player array and draw the
 * avatar the game stage's `PlayerInfo` packet describes.
 */
val LOGIN_SUCCESS_TRAILER: ByteArray = loginSuccessTrailer(PlayerRank.PLAYER)

/** Index of the rev-239 rights byte in the complete span+login-info trailer. */
internal const val LOGIN_RIGHTS_TRAILER_OFFSET = 6
internal const val LOGIN_PLAYER_MOD_TRAILER_OFFSET = 7

private fun loginSuccessTrailer(rank: PlayerRank): ByteArray =
    byteArrayOf(LOGIN_INFO_SPAN.toByte()) + buildLoginInfoBlock(rank)

private fun buildLoginInfoBlock(rank: PlayerRank): ByteArray {
    val block = ByteArray(LOGIN_INFO_PAYLOAD_LENGTH)
    block[RIGHTS_OFFSET] = rank.loginStaffModLevel.toByte()
    block[PLAYER_MOD_OFFSET] = if (rank.loginPlayerModerator) 1 else 0
    block[DI_OFFSET] = (LOCAL_PLAYER_INDEX ushr 8).toByte()
    block[DI_OFFSET + 1] = LOCAL_PLAYER_INDEX.toByte()
    return block
}

/**
 * Handles an op-16/18 login block once the opcode byte itself has already been consumed by the
 * caller (see `Main.kt`'s dispatch, mirroring how `performLoginInit`/`performHandshake` own their
 * own opcode's payload). Reads the u16 frame length + that many payload bytes, decrypts/parses it
 * via [LoginBlockParser], and:
 *  - on success: verifies the echoed server key (warns but proceeds regardless — milestone-3
 *    auto-accepts any credentials, see the plan), builds the inbound/outbound ISAAC ciphers, and
 *    replies response code 2 — followed by [LOGIN_SUCCESS_TRAILER] only on a FRESH login.
 *  - on failure: logs only structural diagnostics, never the credential-bearing packet bytes.
 *
 * [reconnect] MUST be true for an op-18 block: the decompiled client's response-2 dispatch routes a
 * reconnecting client (`ol.cl` set) to state `cd.aj`, which consumes NO bytes after the response
 * code — only the fresh path (`cd.az` -> `cd.am`) reads the advertised span and 34-byte login-info
 * payload. Sending the trailer on reconnect shifts the whole game stream by 35 bytes (observed live as the client
 * mis-framing our REBUILD_NORMAL and throwing `dy.ae: 773 4614`). See [ReconnectLoginTest].
 *
 * Returns the [AuthenticatedGameLogin] for the ensuing game stage on success, or null on failure — the
 * caller is expected to close the connection when this returns null.
 */
suspend fun performLoginBlock(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    expectedServerKey: Long,
    rsaKeyPair: RsaKeyPair,
    reconnect: Boolean = false,
    authenticate: (String, CharArray) -> AuthenticationResult,
): AuthenticatedGameLogin? {
    val length = readU16(read)
    logger.debug { "login block: framed u16 length=$length; reading that many payload bytes" }
    val payload = ByteArray(length)
    read.readFully(payload)
    val payloadSize = payload.size
    val parsedResult = LoginBlockParser.parse(payload, rsaKeyPair.modulus, rsaKeyPair.privateExp)
    payload.fill(0)

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
                val authentication = withContext(Dispatchers.IO) {
                    authenticate(parsed.username, parsed.password)
                }
                if (authentication !is AuthenticationResult.Authenticated) {
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

                val inbound = IsaacCipher(parsed.seeds)
                val outbound = IsaacCipher(IntArray(parsed.seeds.size) { parsed.seeds[it] + 50 })
                logger.info {
                    "login authenticated. Sending response code ${LoginResponse.SUCCESS}" +
                        if (reconnect) " (reconnect: no trailer)." else " + trailer."
                }
                write.writeFully(
                    LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(LoginResponse.SUCCESS)),
                )
                if (!reconnect) write.writeFully(loginSuccessTrailer(authentication.player.rank))
                write.flush()

                AuthenticatedGameLogin(inbound, outbound, authentication.player)
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

private const val RIGHTS_OFFSET = LOGIN_RIGHTS_TRAILER_OFFSET - 1
private const val PLAYER_MOD_OFFSET = LOGIN_PLAYER_MOD_TRAILER_OFFSET - 1

private val PlayerRank.loginStaffModLevel: Int
    get() = when (this) {
        PlayerRank.PLAYER -> 0
        PlayerRank.MODERATOR -> 1
        PlayerRank.ADMINISTRATOR -> 2
    }

private val PlayerRank.loginPlayerModerator: Boolean
    get() = this != PlayerRank.PLAYER
