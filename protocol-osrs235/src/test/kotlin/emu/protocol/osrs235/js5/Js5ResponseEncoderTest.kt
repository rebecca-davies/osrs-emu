package emu.protocol.osrs235.js5

import emu.crypto.NopStreamCipher
import emu.crypto.XorStreamCipher
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

    @Test fun `prefetch does not alter the compression byte`() {
        // The rev-239 injected client rejects any response whose compression byte has the 0x80
        // ("prefetch") bit set: it never masks that bit off, so it reads an invalid compression type,
        // discards the group, re-requests it, and after enough such failures reports
        // error_game_js5crc (verified end-to-end — setting 0x80 blocked the login screen, removing it
        // reaches it). Prefetch and urgent responses must be byte-identical for a given group; the
        // request opcode (0 vs 1) is the only prefetch signal the client uses.
        val urgent = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(2, 3, container(4), false))
        val prefetch = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(2, 3, container(4), true))
        assertEquals(0, prefetch[3].toInt() and 0xFF)
        assertEquals(urgent.toList(), prefetch.toList())
    }

    @Test fun `large response inserts 0xFF at each block after the first`() {
        val body = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(5, 1, container(600), false))
        assertEquals(609, body.size)
        assertEquals(0xFF, body[512].toInt() and 0xFF)
    }

    // A stored container carries a 2-byte version trailer past its header-declared length; the JS5
    // client reads only the declared length, so the trailer must be dropped or the block stream
    // desyncs (client -> error_game_js5crc / js5io).
    @Test fun `version trailer is stripped to the header-declared length`() {
        val withTrailer = container(10) + byteArrayOf(0x12, 0x34) // 15-byte container + 2 version bytes
        val body = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(8, 0, withTrailer, false))
        assertEquals(18, body.size) // 3 header + 15 container, trailer dropped
    }

    // Control opcode 4 sets a per-connection XOR key; every outgoing byte is XORed with it, and the
    // XOR is self-inverse so decrypting reproduces the plaintext response.
    @Test fun `xor key obfuscates every byte and round-trips`() {
        val plain = Js5ResponseEncoder.encode(NopStreamCipher, Js5GroupResponse(2, 3, container(20), false))
        val enc = Js5ResponseEncoder.encode(XorStreamCipher(0x7F), Js5GroupResponse(2, 3, container(20), false))
        assertEquals(plain.size, enc.size)
        // Every byte differs from plaintext (XORed with a non-zero key)...
        assertEquals(plain.indices.count { (plain[it].toInt() xor 0x7F).toByte() == enc[it] }, enc.size)
        // ...and decrypting reproduces the plaintext exactly.
        val dec = ByteArray(enc.size) { (enc[it].toInt() xor 0x7F).toByte() }
        assertEquals(plain.toList(), dec.toList())
    }
}
