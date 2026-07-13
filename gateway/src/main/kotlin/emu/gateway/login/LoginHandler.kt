package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.login.LoginResponse
import emu.protocol.osrs239.login.LoginResponseEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully

/** The two ISAAC ciphers established once a login block is accepted (rev239-login-facts.md §4). */
data class GameCiphers(val inbound: IsaacCipher, val outbound: IsaacCipher)

// The bytes written right after the response-2 code byte. rev239-login-facts.md §6 confirms the
// client reads exactly one response byte via `uo2.af()` and then, on code 2, transitions straight
// into its in-game states — it does NOT document a further rank/flags read at that call site, but
// other OSRS revisions' login-success replies carry a trailer (player rank + an account-flags
// byte) before the client starts reading world/player-info packets. [0, 0] is a starting guess
// (rank=0 i.e. normal player, flags=0) for Task 7 to confirm/adjust empirically against the real
// client — named here (not inlined) specifically so it's a one-line edit.
val LOGIN_SUCCESS_TRAILER: ByteArray = byteArrayOf(0, 0)

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
    val payload = ByteArray(length)
    read.readFully(payload)

    return when (val result = LoginBlockParser.parse(payload, rsaKeyPair.modulus, rsaKeyPair.privateExp)) {
        is LoginBlockParser.Result.BadMagic -> {
            println(
                "LOGIN BLOCK REJECTED: RSA decrypt did not yield magic byte 1 (got ${result.magicByte}) " +
                    "using cleartext-header offset ${result.headerSizeUsed} " +
                    "(LoginBlockParser.CLEARTEXT_HEADER_SIZE) — adjust that constant against the real " +
                    "client's captured bytes. raw payload (${payload.size}B): ${result.payloadHex}",
            )
            null
        }
        is LoginBlockParser.Result.Malformed -> {
            println("LOGIN BLOCK MALFORMED: ${result.reason}. raw payload (${payload.size}B): ${result.payloadHex}")
            null
        }
        is LoginBlockParser.Result.Ok -> {
            val parsed = result.parsed
            if (parsed.serverKey != expectedServerKey) {
                println(
                    "WARNING: login block echoed server key ${parsed.serverKey} but this connection " +
                        "was sent $expectedServerKey — proceeding anyway (milestone-3 auto-accepts any " +
                        "credentials; see docs/superpowers/plans/2026-07-14-login-handshake.md).",
                )
            }
            // Encryptor(client->server) = raw seeds => our INBOUND cipher; decryptor(server->client)
            // = seeds+50 => our OUTBOUND cipher (rev239-login-facts.md §4).
            val inbound = IsaacCipher(parsed.seeds)
            val outbound = IsaacCipher(IntArray(parsed.seeds.size) { parsed.seeds[it] + 50 })

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
