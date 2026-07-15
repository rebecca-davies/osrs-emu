package emu.protocol.osrs239.login.codec

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.login.message.LoginResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginResponseEncoderTest {
    @Test fun `success code 2 encodes as a single byte`() {
        val body = LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(2))
        assertEquals(byteArrayOf(2).toList(), body.toList())
    }

    @Test fun `arbitrary code encodes as that single byte`() {
        val body = LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(21))
        assertEquals(byteArrayOf(21).toList(), body.toList())
    }

    @Test fun `response code must fit its unsigned wire byte`() {
        assertFailsWith<IllegalArgumentException> { LoginResponse(-1) }
        assertFailsWith<IllegalArgumentException> { LoginResponse(256) }
    }
}
