package emu.protocol.osrs239.js5

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot

/**
 * Encodes a [Js5GroupResponse] as the byte-blocked JS5 wire stream: `[archive][group hi][group
 * lo]` followed by the served container bytes (see [servedBytes]), split into a 512-byte block
 * followed by 511-byte blocks each prefixed with an `0xFF` continuation marker, then XORed with the
 * connection's JS5 obfuscation key (control opcode 4; a key of 0 / [emu.crypto.NopStreamCipher]
 * leaves the bytes untouched).
 *
 * `message.prefetch` is intentionally not read here — prefetch and urgent responses are
 * byte-identical on the wire for rev 239. It is kept on the message as a meaningful request
 * attribute for potential future bandwidth prioritization (rsprot-style), not because encoding
 * needs it.
 */
object Js5ResponseEncoder : MessageEncoder<Js5GroupResponse> {
    override val prot: Prot = Js5Prot.GROUP_RESPONSE

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
     * exactly that, never the 2-byte version trailer. This returns that exact slice (dropping the
     * trailer) so the client's 512-byte block-read stays aligned with what we actually sent;
     * serving the trailer would leave 2 stray bytes that desync the next group. Index groups
     * (archive 255) have no trailer, so served == size and nothing is dropped.
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
