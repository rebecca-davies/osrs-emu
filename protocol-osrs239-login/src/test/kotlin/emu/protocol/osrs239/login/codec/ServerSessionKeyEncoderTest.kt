package emu.protocol.osrs239.login.codec

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.login.message.ServerSessionKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerSessionKeyEncoderTest {
    @Test fun `encodes status byte 0 then the key as a big-endian long`() {
        val key = 0x0102030405060708L
        val body = ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(key))
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7, 8),
            body.map { it.toInt() and 0xFF },
        )
    }

    @Test fun `all-ones key round-trips through the 9-byte layout`() {
        val body = ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(-1L))
        assertEquals(9, body.size)
        assertEquals(0, body[0].toInt())
        assertEquals(List(8) { 0xFF }, body.drop(1).map { it.toInt() and 0xFF })
    }

    @Test fun `zero key encodes as nine zero bytes`() {
        val body = ServerSessionKeyEncoder.encode(NopStreamCipher, ServerSessionKey(0L))
        assertEquals(ByteArray(9).toList(), body.toList())
    }
}
