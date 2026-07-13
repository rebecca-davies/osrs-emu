package emu.gateway.js5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Js5ResponseTest {
    // A container: compression byte 0 (none) + 4-byte length + data.
    private fun container(dataLen: Int): ByteArray {
        val out = ByteArray(1 + 4 + dataLen)
        out[0] = 0                       // compression = none
        out[1] = (dataLen ushr 24).toByte()
        out[2] = (dataLen ushr 16).toByte()
        out[3] = (dataLen ushr 8).toByte()
        out[4] = dataLen.toByte()
        for (i in 0 until dataLen) out[5 + i] = (i % 256).toByte()
        return out
    }

    @Test fun `small response is header plus container with no block markers`() {
        val c = container(10)                 // total stream = 3 header + 15 container = 18 < 512
        val out = Js5Response.encode(255, 255, c, prefetch = false)
        assertEquals(18, out.size)
        assertEquals(255, out[0].toInt() and 0xFF)          // archive
        assertEquals(255, ((out[1].toInt() and 0xFF) shl 8) or (out[2].toInt() and 0xFF)) // group
        assertEquals(0, out[3].toInt() and 0xFF)            // compression byte, prefetch=false
        // no 0xFF markers because the whole stream fits in the first 512-byte block
    }

    @Test fun `prefetch sets 0x80 on the compression byte`() {
        val out = Js5Response.encode(2, 3, container(4), prefetch = true)
        assertEquals(0x80, out[3].toInt() and 0xFF)         // 0 | 0x80
    }

    @Test fun `large response inserts 0xFF marker at each 512-byte block after the first`() {
        // stream length target: header(3) + container. Choose data so stream just exceeds 512.
        val c = container(600)                 // stream = 3 + 605 = 608 bytes
        val out = Js5Response.encode(5, 1, c, prefetch = false)
        // Block 1 = 512 wire bytes (no marker). Then 1 marker + remaining (608-512)=96 bytes.
        // Wire size = 512 + 1 + 96 = 609.
        assertEquals(609, out.size)
        assertEquals(0xFF, out[512].toInt() and 0xFF)       // marker at wire offset 512
    }

    @Test fun `stream byte immediately before a marker is the 512th stream byte`() {
        val c = container(600)
        val out = Js5Response.encode(5, 1, c, prefetch = false)
        // Reconstruct the stream by stripping the marker and compare to expected header+container.
        val stream = ArrayList<Byte>()
        var i = 0
        var wireBlock = 0
        while (i < out.size) {
            if (wireBlock > 0) { assertEquals(0xFF, out[i].toInt() and 0xFF); i++ }
            val take = minOf(if (wireBlock == 0) 512 else 511, out.size - i)
            for (k in 0 until take) stream.add(out[i + k])
            i += take
            wireBlock++
        }
        val expected = ByteArray(3 + c.size)
        expected[0] = 5; expected[1] = 0; expected[2] = 1
        c.copyInto(expected, 3)
        assertTrue(expected.toList() == stream)
    }
}
