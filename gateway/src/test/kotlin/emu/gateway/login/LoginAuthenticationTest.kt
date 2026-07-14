package emu.gateway.login

import emu.buffer.JagexBuffer
import emu.crypto.Rsa
import emu.crypto.RsaKeyPair
import emu.crypto.Xtea
import emu.persistence.AuthenticationResult
import emu.persistence.PlayerRank
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginAuthenticationTest {
    @Test fun `administrator rank is written into the fresh-login rights byte`() = runBlocking {
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
                AuthenticationResult.Authenticated(
                    TEST_PLAYER.copy(rank = PlayerRank.ADMINISTRATOR),
                    created = false,
                )
            },
        )

        assertEquals(PlayerRank.ADMINISTRATOR, requireNotNull(login).player.rank)
        assertEquals(2, write.readByte().toInt() and 0xFF)
        val trailer = ByteArray(LOGIN_SUCCESS_TRAILER.size)
        write.readFully(trailer)
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
                AuthenticationResult.InvalidCredentials
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
        val rsa = JagexBuffer.alloc(1 + 16 + 8 + 1 + 1 + password.length + 1)
        rsa.writeByte(1)
        for (seed in seeds) rsa.writeInt(seed)
        rsa.writeLong(serverKey)
        rsa.writeByte(0)
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
