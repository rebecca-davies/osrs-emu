package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import java.math.BigInteger

/**
 * Pure parser for an op-16/18 login block **payload** — the bytes after the opcode byte and the
 * outer u16 frame length have already been stripped by the caller (see [performLoginBlock] /
 * `Main.kt`). No sockets, no I/O; a `ByteArray` in, a [Result] out.
 *
 * Wire layout (docs/superpowers/research/2026-07-14-rev239-login-facts.md §5):
 * ```
 * [cleartext header, CLEARTEXT_HEADER_SIZE bytes: revision int, subversion int, build/flags int,
 *  3 flag bytes]
 * [u16 rsaLength][rsaLength bytes of RSA ciphertext]
 * [XTEA-encrypted tail — ignored this milestone, see §5; not needed until the game-packet
 *  milestone reads username/prefs/CRCs from it]
 * ```
 *
 * RSA plaintext, after decrypting the ciphertext block with our private key (§2):
 * `[1 magic][seed0 int][seed1 int][seed2 int][seed3 int][serverKey long][auth-method byte][marker
 * byte][password C-string]`.
 *
 * **Header-offset uncertainty (empirical stance, see the plan/design docs):** the exact size of
 * the cleartext header before the RSA block was derived from the decompile (§5: 4+4+4+1+1+1 = 15
 * bytes) but not yet confirmed against a real captured client packet — that confirmation is
 * Task 7's job. [CLEARTEXT_HEADER_SIZE] is a single named constant so it is trivial to retune
 * without touching parsing logic. If the magic-byte check fails, [parse] returns [Result.BadMagic]
 * with the raw payload hex and the header size that was tried, so the caller can log it for
 * Task 7 to diagnose against the real client's bytes.
 *
 * **Auth-token-section assumption:** the bytes between `serverKey` and the string-marker byte vary
 * by login type (reconnect vs. new-login) and authenticator method (§2). This parser assumes the
 * common **new-login, no-authenticator** path, where that section is a single auth-method byte
 * (value 0 selects the "no extra fields" case in the decompiled switch). Task 7 must confirm this
 * against the real client; if it captures a non-zero auth-method byte, this parser needs a
 * corresponding branch.
 */
object LoginBlockParser {
    // See the class doc: revision(4) + subversion(4) + build/flags(4) + 3 flag bytes.
    const val CLEARTEXT_HEADER_SIZE = 15

    data class Parsed(val seeds: IntArray, val serverKey: Long, val password: String)

    sealed class Result {
        data class Ok(val parsed: Parsed) : Result()

        /** RSA-decrypt succeeded but the first plaintext byte wasn't the expected magic (1). */
        data class BadMagic(val magicByte: Int, val headerSizeUsed: Int, val payloadHex: String) : Result()

        /** Payload too short, declared lengths out of range, or an unexpected exception while parsing. */
        data class Malformed(val reason: String, val payloadHex: String) : Result()
    }

    fun parse(payload: ByteArray, modulus: BigInteger, privateExp: BigInteger): Result {
        if (payload.size < CLEARTEXT_HEADER_SIZE + 2) {
            return Result.Malformed(
                "payload (${payload.size} bytes) shorter than header ($CLEARTEXT_HEADER_SIZE) + u16 length prefix",
                payload.toHexLog(),
            )
        }
        return try {
            val buf = JagexBuffer(payload, pos = CLEARTEXT_HEADER_SIZE)
            val rsaLength = buf.readUShort()
            if (rsaLength < 0 || buf.readableBytes() < rsaLength) {
                return Result.Malformed(
                    "declared rsaLength=$rsaLength exceeds remaining payload (${buf.readableBytes()} bytes)",
                    payload.toHexLog(),
                )
            }
            val cipherBytes = buf.readBytes(rsaLength)
            val plain = Rsa.decrypt(cipherBytes, modulus, privateExp)
            if (plain.isEmpty() || plain[0].toInt() != 1) {
                val magic = if (plain.isNotEmpty()) plain[0].toInt() else -1
                return Result.BadMagic(magic, CLEARTEXT_HEADER_SIZE, payload.toHexLog())
            }

            val pb = JagexBuffer(plain, pos = 1)
            val minRemaining = 4 * 4 + 8 + 1 + 1 // 4 seed ints + server key long + auth byte + marker byte
            if (pb.readableBytes() < minRemaining) {
                return Result.Malformed(
                    "RSA plaintext (${plain.size} bytes) too short for seeds+serverKey+auth+marker",
                    payload.toHexLog(),
                )
            }
            val seeds = IntArray(4) { pb.readInt() }
            val serverKey = pb.readLong()
            pb.readUByte() // auth-method byte (assumed 0 = no authenticator; see class doc)
            pb.readUByte() // string-type marker byte
            val password = pb.readCString()
            Result.Ok(Parsed(seeds, serverKey, password))
        } catch (e: Exception) {
            Result.Malformed("exception while parsing: ${e.javaClass.simpleName}: ${e.message}", payload.toHexLog())
        }
    }
}

fun ByteArray.toHexLog(): String = joinToString(" ") { "%02x".format(it) }
