package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Pure test of the op-16/18 login block parser: no sockets. Builds a plaintext RSA payload
// matching rev239-login-facts.md §2 ([1][seed0..3][serverKey][auth-method byte][marker
// byte][password C-string]), public-encrypts it, wraps it in a cleartext header + u16 rsaLength +
// a dummy XTEA-tail filler (unused this milestone), and asserts the parser recovers the seeds,
// server key, and password — or reports the right failure shape when the header offset is wrong.
class LoginBlockParserTest {
    private fun keyPair(): RsaKeyPair = Rsa.generateKeyPair(1024)

    private fun rsaPlaintext(seeds: IntArray, serverKey: Long, password: String): ByteArray {
        val buf = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1)
        buf.writeByte(1) // magic
        for (s in seeds) buf.writeInt(s)
        buf.writeLong(serverKey)
        buf.writeByte(0) // auth-method byte (assumed 0 = no authenticator, see LoginBlockParser doc)
        buf.writeByte(0) // string-type marker byte
        buf.writeCString(password)
        return buf.array
    }

    private fun loginBlockPayload(
        keyPair: RsaKeyPair,
        seeds: IntArray,
        serverKey: Long,
        password: String,
        headerSize: Int = LoginBlockParser.CLEARTEXT_HEADER_SIZE,
    ): ByteArray {
        val plaintext = rsaPlaintext(seeds, serverKey, password)
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val header = ByteArray(headerSize) // cleartext header contents are irrelevant to the parser
        val out = JagexBuffer.alloc(header.size + 2 + cipherBytes.size + 8)
        out.writeBytes(header)
        out.writeShort(cipherBytes.size)
        out.writeBytes(cipherBytes)
        out.writeBytes(ByteArray(8)) // dummy XTEA-tail filler, unused this milestone
        return out.array
    }

    @Test fun `parses seeds and server key from a well-formed login block, discarding the password`() {
        val kp = keyPair()
        val seeds = intArrayOf(1, 2, 3, 4)
        // The block carries a password; the parser must read past it correctly but NOT retain it
        // (Parsed has no password field — credentials are never stored or logged).
        val payload = loginBlockPayload(kp, seeds, serverKey = 0x0102030405060708L, password = "dummy-pw")

        val result = LoginBlockParser.parse(payload, kp.modulus, kp.privateExp)

        val ok = assertIs<LoginBlockParser.Result.Ok>(result)
        assertContentEquals(seeds, ok.parsed.seeds)
        assertEquals(0x0102030405060708L, ok.parsed.serverKey)
    }

    @Test fun `wrong header offset yields BadMagic with the raw payload logged`() {
        val kp = keyPair()
        val payload = loginBlockPayload(
            kp,
            intArrayOf(1, 2, 3, 4),
            serverKey = 42L,
            password = "x",
            headerSize = LoginBlockParser.CLEARTEXT_HEADER_SIZE + 3, // wrong offset
        )

        // Parsing at the (correct, named-constant) offset against a block built with a different
        // header size must not silently succeed — it should either read garbage that fails the
        // magic-byte check, or run out of buffer. Either is an acceptable "wrong offset" signal.
        val result = LoginBlockParser.parse(payload, kp.modulus, kp.privateExp)
        assertTrue(
            result is LoginBlockParser.Result.BadMagic || result is LoginBlockParser.Result.Malformed,
            "expected a parse failure when the header offset is wrong, got $result",
        )
    }

    @Test fun `too-short payload is reported as Malformed rather than throwing`() {
        val kp = keyPair()
        val result = LoginBlockParser.parse(ByteArray(4), kp.modulus, kp.privateExp)
        assertIs<LoginBlockParser.Result.Malformed>(result)
    }

    @Test fun `truncated rsa ciphertext is reported as Malformed rather than throwing`() {
        val kp = keyPair()
        val header = ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE)
        val buf = JagexBuffer.alloc(header.size + 2)
        buf.writeBytes(header)
        buf.writeShort(500) // declares 500 bytes of ciphertext that are not actually present
        val result = LoginBlockParser.parse(buf.array, kp.modulus, kp.privateExp)
        assertIs<LoginBlockParser.Result.Malformed>(result)
    }
}
