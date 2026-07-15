package emu.server.login

import emu.server.login.wire.LOGIN_PLAYER_MOD_TRAILER_OFFSET
import emu.server.login.wire.LOGIN_RIGHTS_TRAILER_OFFSET
import emu.server.login.wire.loginSuccessTrailer
import emu.server.login.wire.performLoginBlock
import emu.protocol.osrs239.login.codec.LoginBlockParser

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.crypto.Xtea
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticationDecision
import emu.server.login.auth.LoginAuthenticator
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginAuthenticationTest {
    @Test
    fun `opcode 18 is rejected until token scoped reconnect replacement exists`() = runBlocking {
        val read = ByteChannel(autoFlush = true)
        val write = ByteChannel(autoFlush = true)
        read.writeByte(18)

        LoginServer(Rsa.generateKeyPair(1024), LoginAuthenticator(::acceptTestLogin)).use { server ->
            assertNull(server.authenticate(read, write))
        }
    }

    @Test fun `authenticated account carries administrator privilege into login completion`() = runBlocking {
        val keyPair = Rsa.generateKeyPair(1024)
        val seeds = intArrayOf(11, 22, 33, 44)
        val serverKey = 0x0102030405060708L
        val payload = loginPayload(keyPair, seeds, serverKey, "Admin", "dummy-pw")
        val read = ByteChannel(autoFlush = true)
        val write = ByteChannel(autoFlush = true)
        read.writeByte((payload.size ushr 8).toByte())
        read.writeByte(payload.size.toByte())
        read.writeFully(payload)

        val login = performLoginBlock(
            read,
            write,
            serverKey,
            keyPair,
            authenticate = { _, _ ->
                AuthenticationDecision.Authenticated(
                    TEST_ACCOUNT.copy(privilege = AccountPrivilege.ADMINISTRATOR),
                )
            },
        )

        assertEquals(AccountPrivilege.ADMINISTRATOR, requireNotNull(login).account.privilege)
        val trailer = loginSuccessTrailer(requireNotNull(login).account.privilege, playerIndex = 1)
        assertEquals(2, trailer[LOGIN_RIGHTS_TRAILER_OFFSET].toInt() and 0xFF)
        assertEquals(1, trailer[LOGIN_PLAYER_MOD_TRAILER_OFFSET].toInt() and 0xFF)
    }

    @Test
    fun `invalid password sends response three and clears the disposable password`() = runBlocking {
        val keyPair = Rsa.generateKeyPair(1024)
        val seeds = intArrayOf(11, 22, 33, 44)
        val serverKey = 0x0102030405060708L
        val payload = loginPayload(keyPair, seeds, serverKey, "Rebecca_Bird", "dummy-pw")
        val read = ByteChannel(autoFlush = true)
        val write = ByteChannel(autoFlush = true)
        read.writeByte((payload.size ushr 8).toByte())
        read.writeByte(payload.size.toByte())
        read.writeFully(payload)
        var passwordBuffer: CharArray? = null

        val login = performLoginBlock(
            read = read,
            write = write,
            expectedServerKey = serverKey,
            rsaKeyPair = keyPair,
            authenticate = { username, password ->
                assertEquals("Rebecca_Bird", username)
                assertEquals("dummy-pw", password.concatToString())
                passwordBuffer = password
                AuthenticationDecision.Rejected
            },
        )

        assertNull(login)
        assertEquals(3, write.readByte().toInt() and 0xFF)
        assertTrue(requireNotNull(passwordBuffer).all { it == '\u0000' })
    }

    private fun loginPayload(
        keyPair: RsaKeyPair,
        seeds: IntArray,
        serverKey: Long,
        username: String,
        password: String,
    ): ByteArray {
        val rsa = JagexBuffer.alloc(1 + 16 + 8 + 1 + 4 + 1 + password.length + 1)
        rsa.writeByte(1)
        for (seed in seeds) rsa.writeInt(seed)
        rsa.writeLong(serverKey)
        rsa.writeByte(2)
        rsa.writeInt(0)
        rsa.writeByte(0)
        rsa.writeCString(password)
        val cipher = Rsa.crypt(rsa.array, keyPair.modulus, keyPair.publicExp)
        val usernameBytes = username.toByteArray(Charsets.ISO_8859_1) + 0
        val tail = usernameBytes.copyOf((usernameBytes.size + 7) / 8 * 8)
        val encryptedTail = Xtea.encrypt(tail, seeds)
        return JagexBuffer.alloc(LoginBlockParser.CLEARTEXT_HEADER_SIZE + 2 + cipher.size + encryptedTail.size).apply {
            writeBytes(ByteArray(LoginBlockParser.CLEARTEXT_HEADER_SIZE))
            writeShort(cipher.size)
            writeBytes(cipher)
            writeBytes(encryptedTail)
        }.array
    }
}
