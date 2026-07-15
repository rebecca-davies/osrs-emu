package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.Xtea
import java.math.BigInteger

/**
 * Parses an op-16/18 login payload after the opcode and outer u16 frame length.
 *
 * Wire layout:
 * ```
 * [cleartext header, CLEARTEXT_HEADER_SIZE bytes: revision int, subversion int, build/flags int,
 *  3 flag bytes]
 * [u16 rsaLength][rsaLength bytes of RSA ciphertext]
 * [XTEA-encrypted tail beginning with the username C-string]
 * ```
 *
 * RSA plaintext after private-key decryption:
 * `[1 magic][seed0 int][seed1 int][seed2 int][seed3 int][serverKey long][auth token][marker
 * byte][password C-string]`. A fresh login's auth token is one method byte followed by four bytes
 * of method-specific data (or reserved zeroes); a reconnect's token is four ints (16 bytes).
 *
 * The cleartext header is 15 bytes: three ints followed by three flag bytes. A failed RSA magic
 * check returns [Result.BadMagic] without retaining credential-bearing packet bytes.
 *
 * Fresh-login auth methods all occupy four payload bytes. The parser skips those bytes without
 * retaining the authentication token.
 */
object LoginBlockParser {
    /** Three 4-byte fields followed by three flag bytes. */
    const val CLEARTEXT_HEADER_SIZE = 15

    /** Disposable login credentials plus the ISAAC seed material needed after authentication. */
    class Parsed(
        val seeds: IntArray,
        val serverKey: Long,
        val username: String,
        val password: CharArray,
    ) {
        /** Overwrites the sole retained plaintext password buffer immediately after authentication. */
        fun clearPassword() = password.fill('\u0000')

        override fun toString(): String =
            "Parsed(seeds=<redacted>, serverKey=$serverKey, username=<redacted>, password=<redacted>)"
    }

    sealed class Result {
        data class Ok(val parsed: Parsed) : Result()

        /** RSA-decrypt succeeded but the first plaintext byte wasn't the expected magic (1). */
        data class BadMagic(val magicByte: Int, val headerSizeUsed: Int) : Result()

        /** Payload too short, declared lengths out of range, or an unexpected exception while parsing. */
        data class Malformed(val reason: String) : Result()
    }

    fun parse(
        payload: ByteArray,
        modulus: BigInteger,
        privateExp: BigInteger,
        reconnect: Boolean = false,
    ): Result {
        if (payload.size < CLEARTEXT_HEADER_SIZE + 2) {
            return Result.Malformed(
                "payload (${payload.size} bytes) shorter than header ($CLEARTEXT_HEADER_SIZE) + u16 length prefix",
            )
        }
        var password: CharArray? = null
        var passwordTransferred = false
        var rsaPlaintext: ByteArray? = null
        var decryptedTail: ByteArray? = null
        return try {
            val buf = JagexBuffer(payload, pos = CLEARTEXT_HEADER_SIZE)
            val rsaLength = buf.readUShort()
            if (rsaLength < 0 || buf.readableBytes() < rsaLength) {
                return Result.Malformed(
                    "declared rsaLength=$rsaLength exceeds remaining payload (${buf.readableBytes()} bytes)",
                )
            }
            val cipherBytes = buf.readBytes(rsaLength)
            val plain = Rsa.decrypt(cipherBytes, modulus, privateExp).also { rsaPlaintext = it }
            if (plain.isEmpty() || plain[0].toInt() != 1) {
                val magic = if (plain.isNotEmpty()) plain[0].toInt() else -1
                return Result.BadMagic(magic, CLEARTEXT_HEADER_SIZE)
            }

            val pb = JagexBuffer(plain, pos = 1)
            val authTokenLength = if (reconnect) RECONNECT_AUTH_TOKEN_LENGTH else FRESH_AUTH_TOKEN_LENGTH
            val minRemaining = 4 * 4 + 8 + authTokenLength + 1 // seeds + key + auth token + marker
            if (pb.readableBytes() < minRemaining) {
                return Result.Malformed(
                    "RSA plaintext (${plain.size} bytes) too short for seeds+serverKey+auth token+marker",
                )
            }
            val seeds = IntArray(4) { pb.readInt() }
            val serverKey = pb.readLong()
            if (!reconnect) pb.readUByte() // auth-method byte; reconnect has only its four saved ints
            pb.pos += if (reconnect) RECONNECT_AUTH_TOKEN_LENGTH else FRESH_AUTH_PAYLOAD_LENGTH
            pb.readUByte() // string-type marker byte
            password = pb.readSensitiveCString(MAX_PASSWORD_LENGTH)

            if (buf.readableBytes() == 0) error("login block has no XTEA username tail")
            val encryptedTail = buf.readBytes(buf.readableBytes())
            val tail = Xtea.decrypt(encryptedTail, seeds).also { decryptedTail = it }
            val username = JagexBuffer(tail).readRequiredCString(MAX_USERNAME_LENGTH)
            val parsed = Parsed(seeds, serverKey, username, password)
            passwordTransferred = true
            Result.Ok(parsed)
        } catch (e: Exception) {
            Result.Malformed("exception while parsing: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            rsaPlaintext?.fill(0)
            decryptedTail?.fill(0)
            if (!passwordTransferred) password?.fill('\u0000')
        }
    }

    /** Reads an ASCII password without ever constructing an immutable plaintext [String]. */
    private fun JagexBuffer.readSensitiveCString(maxLength: Int): CharArray {
        val start = pos
        while (pos < array.size && array[pos].toInt() != 0) pos++
        require(pos < array.size) { "unterminated password" }
        val length = pos - start
        require(length in 1..maxLength) { "password length outside 1..$maxLength" }
        val value = CharArray(length) { index -> (array[start + index].toInt() and 0xFF).toChar() }
        pos++
        return value
    }

    /** Reads the leading decrypted username and requires its C-string terminator. */
    private fun JagexBuffer.readRequiredCString(maxLength: Int): String {
        val start = pos
        while (pos < array.size && array[pos].toInt() != 0) pos++
        require(pos < array.size) { "unterminated username" }
        val length = pos - start
        require(length in 1..maxLength) { "username length outside 1..$maxLength" }
        val value = String(array, start, length, Charsets.ISO_8859_1)
        pos++
        return value
    }

    private const val MAX_PASSWORD_LENGTH = 128
    private const val MAX_USERNAME_LENGTH = 320
    private const val FRESH_AUTH_PAYLOAD_LENGTH = 4
    private const val FRESH_AUTH_TOKEN_LENGTH = 1 + FRESH_AUTH_PAYLOAD_LENGTH
    private const val RECONNECT_AUTH_TOKEN_LENGTH = 16
}
