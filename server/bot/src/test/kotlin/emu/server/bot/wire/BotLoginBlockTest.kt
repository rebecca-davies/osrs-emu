package emu.server.bot.wire

import emu.crypto.Rsa
import emu.buffer.JagexBuffer
import emu.protocol.osrs239.login.codec.LoginBlockParser
import emu.protocol.osrs239.login.prot.LoginProt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BotLoginBlockTest {
    @Test
    fun `generated payload follows the server rev 239 login parser`() {
        val keyPair = Rsa.generateKeyPair(1_024)
        val seeds = intArrayOf(11, 22, 33, 44)
        val password = "dummy-only-password".toCharArray()

        val payload =
            BotLoginBlock.encode(
                keyPair.publicKey,
                seeds,
                serverKey = 0x0102030405060708,
                username = "Bot123456789",
                password = password,
            )
        val result = LoginBlockParser.parse(payload, keyPair.modulus, keyPair.privateExp)
        val header = JagexBuffer(payload)

        assertEquals(LoginProt.REVISION, header.readInt())
        assertEquals(LoginProt.SUBVERSION, header.readInt())
        assertEquals(LoginProt.BUILD_FLAGS, header.readInt())
        assertEquals(0, header.readUByte())
        assertEquals(0, header.readUByte())
        assertEquals(0, header.readUByte())
        val parsed = assertIs<LoginBlockParser.Result.Ok>(result).parsed
        assertContentEquals(seeds, parsed.seeds)
        assertEquals(0x0102030405060708, parsed.serverKey)
        assertEquals("Bot123456789", parsed.username)
        assertContentEquals(password, parsed.password)
        parsed.clearPassword()
        password.fill('\u0000')
    }
}
