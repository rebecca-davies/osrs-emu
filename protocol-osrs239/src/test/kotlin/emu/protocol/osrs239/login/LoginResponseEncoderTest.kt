package emu.protocol.osrs239.login

import emu.crypto.NopStreamCipher
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginResponseEncoderTest {
    @Test fun `success code 2 encodes as a single byte`() {
        val body = LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(2))
        assertEquals(byteArrayOf(2).toList(), body.toList())
    }

    @Test fun `arbitrary code encodes as that single byte`() {
        val body = LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(21))
        assertEquals(byteArrayOf(21).toList(), body.toList())
    }
}
