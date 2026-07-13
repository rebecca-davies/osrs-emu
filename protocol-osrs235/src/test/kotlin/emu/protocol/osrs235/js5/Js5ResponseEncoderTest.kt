package emu.protocol.osrs235.js5

import emu.crypto.NopStreamCipher
import kotlin.test.Test
import kotlin.test.assertEquals

class Js5ResponseEncoderTest {
    private fun container(dataLen: Int): ByteArray {
        val out = ByteArray(1 + 4 + dataLen)
        out[0] = 0
        out[1] = (dataLen ushr 24).toByte(); out[2] = (dataLen ushr 16).toByte()
        out[3] = (dataLen ushr 8).toByte(); out[4] = dataLen.toByte()
        for (i in 0 until dataLen) out[5 + i] = (i % 256).toByte()
        return out
    }

    @Test fun `small response is header plus container, no markers`() {
        val body = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(255, 255, container(10), false))
        assertEquals(18, body.size)
        assertEquals(255, body[0].toInt() and 0xFF)
        assertEquals(255, ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF))
        assertEquals(0, body[3].toInt() and 0xFF)
    }

    @Test fun `prefetch sets 0x80 on compression byte`() {
        val body = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(2, 3, container(4), true))
        assertEquals(0x80, body[3].toInt() and 0xFF)
    }

    @Test fun `large response inserts 0xFF at each block after the first`() {
        val body = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(5, 1, container(600), false))
        assertEquals(609, body.size)
        assertEquals(0xFF, body[512].toInt() and 0xFF)
    }
}
