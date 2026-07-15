package emu.protocol.osrs239.js5.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.prot.Js5Prot

/**
 * Encodes a [Js5GroupResponse] as the byte-blocked JS5 wire stream: `[archive][group hi][group
 * lo]` followed by the served container bytes (see [servedBytes]), split into a 512-byte block
 * followed by 511-byte blocks each prefixed with an `0xFF` continuation marker, then XORed with the
 * connection's JS5 obfuscation key (control opcode 4; a key of 0 / [emu.crypto.NopStreamCipher]
 * leaves the bytes untouched).
 *
 * Prefetch and urgent responses are byte-identical in rev 239, so [Js5GroupResponse.prefetch] does
 * not affect encoding.
 */
object Js5ResponseEncoder : MessageEncoder<Js5GroupResponse> {
    override val prot: Prot = Js5Prot.GROUP_RESPONSE
    override val messageType = Js5GroupResponse::class.java

    override fun encode(cipher: StreamCipher, message: Js5GroupResponse): ByteArray {
        val c = servedBytes(message.container)
        val stream = ByteArray(3 + c.size)
        stream[0] = message.archive.toByte()
        stream[1] = (message.group ushr 8).toByte()
        stream[2] = message.group.toByte()
        c.copyInto(stream, 3)

        val outSize = if (stream.size <= 512) stream.size else stream.size + (stream.size - 512 + 510) / 511
        val out = ByteArray(outSize)
        var pos = 0; var block = 0; var outPos = 0
        while (pos < stream.size) {
            if (block > 0) out[outPos++] = 0xFF.toByte()
            val take = minOf(if (block == 0) 512 else 511, stream.size - pos)
            System.arraycopy(stream, pos, out, outPos, take)
            pos += take; outPos += take; block++
        }
        for (i in out.indices) out[i] = (out[i].toInt() xor (cipher.nextInt() and 0xFF)).toByte()
        return out
    }

    /**
     * A stored JS5 container is `[compression:1][compressedLength:4][payload][optional
     * version:2]`. The client sizes each group from its header — `compressedLength + 5`
     * (uncompressed) or `+ 9` (compressed: 4 extra bytes for the decompressed length) — and reads
     * exactly that, never the 2-byte version trailer. Returns that slice so block reads remain
     * aligned. Index groups have no trailer.
     */
    private fun servedBytes(c: ByteArray): ByteArray {
        if (c.size < 5) return c
        val compression = c[0].toInt() and 0xFF
        val compressedLength = ((c[1].toInt() and 0xFF) shl 24) or ((c[2].toInt() and 0xFF) shl 16) or
            ((c[3].toInt() and 0xFF) shl 8) or (c[4].toInt() and 0xFF)
        val served = (if (compression == 0) 5 else 9) + compressedLength
        return if (served in 1 until c.size) c.copyOf(served) else c
    }
}
