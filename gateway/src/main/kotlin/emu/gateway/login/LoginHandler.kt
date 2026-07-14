package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully

private val logger = KotlinLogging.logger {}

/** The two ISAAC ciphers established once a login block is accepted (rev239-login-facts.md §4). */
data class GameCiphers(val inbound: IsaacCipher, val outbound: IsaacCipher)

/**
 * The local player's index into the client's 2048-slot player array (`client.di`,
 * docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §2/§4a). This is the one
 * load-bearing field in the login-info trailer ([LOGIN_SUCCESS_TRAILER]) — the game stage's
 * initial `RebuildNormal`/`PlayerInfo` packets describe this same local player. Milestone-3 has no
 * persisted account/slot table, so every connection is simply assigned index 1 (a valid non-zero
 * slot; index 0 is conventionally reserved).
 */
const val LOCAL_PLAYER_INDEX: Int = 1

/** Fixed length (in bytes) of the login-info block that follows the length byte itself. */
private const val LOGIN_INFO_BLOCK_LENGTH = 37

/** Byte offset of the 2-byte `di` field within the login-info block (ingame-facts.md §2). */
private const val DI_OFFSET = 7

/**
 * The bytes written right after the response-2 code byte.
 *
 * Task 7 (empirical, against the real rev-239 client, decompiled `client.java`): on response code 2
 * the client (non-BETA world => state `cd.az`) reads ONE length byte that must equal `jz.ax.av`
 * (opcode 21's fixed payload length = 37); a mismatch calls `xz.ai(byte)` and bounces to the login
 * screen. It then (state `cd.am`) waits until 37 bytes are available and parses a fixed login-info
 * block:
 * ```
 * [1] account-hash-present flag  [4] account hash (read regardless of the flag)
 * [1] jo  [1] member flag  [2] di (u16)  [1] ef
 * [8] long qo  [8] long nq  [8] long nh              (= 34 parsed bytes; 3 trailing pad to 37)
 * ```
 * Every field zero-fills for milestone-3 (auto-accept, no persisted account) except `di`, which
 * carries [LOCAL_PLAYER_INDEX] — the client needs this to index its own player array and draw the
 * avatar the game stage's `PlayerInfo` packet describes.
 */
val LOGIN_SUCCESS_TRAILER: ByteArray = byteArrayOf(LOGIN_INFO_BLOCK_LENGTH.toByte()) + buildLoginInfoBlock()

private fun buildLoginInfoBlock(): ByteArray {
    val block = ByteArray(LOGIN_INFO_BLOCK_LENGTH)
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
 *    replies response code 2 (+ [LOGIN_SUCCESS_TRAILER]).
 *  - on failure: logs the parser's diagnostic (raw payload hex + header offset attempted, so
 *    Task 7 can correct [LoginBlockParser.CLEARTEXT_HEADER_SIZE] against the real client) and
 *    writes nothing.
 *
 * Returns the [GameCiphers] for the ensuing game stage on success, or null on any failure — the
 * caller is expected to close the connection when this returns null.
 */
suspend fun performLoginBlock(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    expectedServerKey: Long,
    rsaKeyPair: RsaKeyPair,
): GameCiphers? {
    val length = readU16(read)
    logger.debug { "login block: framed u16 length=$length; reading that many payload bytes" }
    val payload = ByteArray(length)
    read.readFully(payload)

    return when (val result = LoginBlockParser.parse(payload, rsaKeyPair.modulus, rsaKeyPair.privateExp)) {
        is LoginBlockParser.Result.BadMagic -> {
            logger.warn {
                "login block rejected: RSA decrypt did not yield magic byte 1 (got ${result.magicByte}) " +
                    "using cleartext-header offset ${result.headerSizeUsed} " +
                    "(LoginBlockParser.CLEARTEXT_HEADER_SIZE) — adjust that constant against the real " +
                    "client's captured bytes. raw payload (${payload.size}B): ${result.payloadHex}"
            }
            null
        }
        is LoginBlockParser.Result.Malformed -> {
            logger.warn { "login block malformed: ${result.reason}. raw payload (${payload.size}B): ${result.payloadHex}" }
            null
        }
        is LoginBlockParser.Result.Ok -> {
            logger.debug { "login block: ${payload.size} bytes, decrypt OK" }
            val parsed = result.parsed
            if (parsed.serverKey != expectedServerKey) {
                logger.warn {
                    "login block echoed server key ${parsed.serverKey} but this connection was sent " +
                        "$expectedServerKey — proceeding anyway (milestone-3 auto-accepts any credentials; " +
                        "see docs/superpowers/plans/2026-07-14-login-handshake.md)."
                }
            }
            // Encryptor(client->server) = raw seeds => our INBOUND cipher; decryptor(server->client)
            // = seeds+50 => our OUTBOUND cipher (rev239-login-facts.md §4).
            val inbound = IsaacCipher(parsed.seeds)
            val outbound = IsaacCipher(IntArray(parsed.seeds.size) { parsed.seeds[it] + 50 })

            // NEVER log the password (or any credential). Milestone-3 auto-accepts, so we do not
            // even need it — logging it would leak a real user's password.
            logger.info { "login block OK: magic=1. Sending response code 2 + trailer." }
            write.writeFully(LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(2)))
            write.writeFully(LOGIN_SUCCESS_TRAILER)
            write.flush()

            GameCiphers(inbound, outbound)
        }
    }
}

private suspend fun readU16(read: ByteReadChannel): Int {
    val hi = read.readByte().toInt() and 0xFF
    val lo = read.readByte().toInt() and 0xFF
    return (hi shl 8) or lo
}
