package emu.protocol.osrs239.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LoginCodecsTest {
    @Test
    fun `repository contains every declared login encoder`() {
        val repository = buildLoginCodecRepository()

        LoginCodecs.encoders.forEach { encoder ->
            assertSame(encoder, repository.encoder(encoder.messageType))
        }
        assertEquals(2, LoginCodecs.encoders.size)
    }
}
