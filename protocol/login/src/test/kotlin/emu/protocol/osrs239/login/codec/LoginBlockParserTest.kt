package emu.protocol.osrs239.login.codec

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.crypto.Xtea
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Pure test of the op-16/18 login block parser: no sockets. Builds a plaintext RSA payload
// matching rev239-login-facts.md §2 ([1][seed0..3][serverKey][auth-method byte][4-byte auth
// payload][marker byte][password C-string]), public-encrypts it, wraps it in a cleartext header + u16 rsaLength +
// an encrypted username tail, and asserts the parser recovers disposable credentials — or reports
// the right failure shape when the header offset is wrong.
class LoginBlockParserTest {
    private fun keyPair(): RsaKeyPair = Rsa.generateKeyPair(1024)

    private fun rsaPlaintext(
        seeds: IntArray,
        serverKey: Long,
        password: String,
        authPayload: ByteArray = ByteArray(4),
    ): ByteArray {
        require(authPayload.size == 4)
        val buf = JagexBuffer.alloc(1 + 16 + 8 + 1 + authPayload.size + 1 + password.length + 1)
        buf.writeByte(1) // magic
        for (s in seeds) buf.writeInt(s)
        buf.writeLong(serverKey)
        buf.writeByte(2) // auth-method wire value for client.dm case 0
        buf.writeBytes(authPayload) // rev 239 always reserves four bytes for the auth method payload
        buf.writeByte(0) // string-type marker byte
        buf.writeCString(password)
        return buf.array
    }

    private fun loginBlockPayload(
        keyPair: RsaKeyPair,
        seeds: IntArray,
        serverKey: Long,
        password: String,
        username: String = "Rebecca_Bird",
        headerSize: Int = LoginBlockParser.CLEARTEXT_HEADER_SIZE,
        authPayload: ByteArray = ByteArray(4),
    ): ByteArray {
        val plaintext = rsaPlaintext(seeds, serverKey, password, authPayload)
        val cipherBytes = Rsa.crypt(plaintext, keyPair.modulus, keyPair.publicExp)
        val header = ByteArray(headerSize) // cleartext header contents are irrelevant to the parser
        val usernameBytes = username.toByteArray(Charsets.ISO_8859_1) + 0
        val tail = usernameBytes.copyOf((usernameBytes.size + 7) / 8 * 8)
        val encryptedTail = Xtea.encrypt(tail, seeds)
        val out = JagexBuffer.alloc(header.size + 2 + cipherBytes.size + encryptedTail.size)
        out.writeBytes(header)
        out.writeShort(cipherBytes.size)
        out.writeBytes(cipherBytes)
        out.writeBytes(encryptedTail)
        return out.array
    }

    @Test fun `parses username and disposable password from a well-formed login block`() {
        val kp = keyPair()
        val seeds = intArrayOf(1, 2, 3, 4)
        val payload = loginBlockPayload(kp, seeds, serverKey = 0x0102030405060708L, password = "dummy-pw")

        val result = LoginBlockParser.parse(payload, kp.modulus, kp.privateExp)

        val ok = assertIs<LoginBlockParser.Result.Ok>(result)
        assertContentEquals(seeds, ok.parsed.seeds)
        assertEquals(0x0102030405060708L, ok.parsed.serverKey)
        assertEquals("Rebecca_Bird", ok.parsed.username)
        assertContentEquals("dummy-pw".toCharArray(), ok.parsed.password)

        ok.parsed.clearPassword()
        assertTrue(ok.parsed.password.all { it == '\u0000' })
    }

    @Test fun `skips the four-byte rev 239 authentication payload before the password`() {
        val kp = keyPair()
        val payload = loginBlockPayload(
            kp,
            intArrayOf(11, 22, 33, 44),
            serverKey = 99L,
            password = "dummy-pw",
            authPayload = byteArrayOf(0x12, 0x34, 0x56, 0x78),
        )

        val result = LoginBlockParser.parse(payload, kp.modulus, kp.privateExp)

        val ok = assertIs<LoginBlockParser.Result.Ok>(result)
        assertContentEquals("dummy-pw".toCharArray(), ok.parsed.password)
        ok.parsed.clearPassword()
    }

    @Test fun `wrong header offset yields a structural failure without retaining payload bytes`() {
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
