package emu.protocol.osrs235.js5

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot

object Js5ResponseEncoder : MessageEncoder<Js5GroupResponse> {
    override val prot: Prot = Js5Prot.GROUP_RESPONSE

    override fun encode(cipher: StreamCipher, message: Js5GroupResponse): ByteArray {
        // Note: message.prefetch is intentionally not read here — prefetch and urgent responses are
        // byte-identical on the wire for rev 239 (see the 0x80 bit history below). It is kept on the
        // message as a meaningful request attribute for potential future bandwidth prioritization
        // (rsprot-style), not because encoding needs it.
        // Serve exactly the header-declared container length (compression + 4-byte length header,
        // then 5|9 + compressedLength of payload), dropping the stored 2-byte version trailer. The
        // client reads precisely this many container bytes per group and blocks at 512-byte
        // boundaries of it; sending the trailer would leave 2 stray bytes that desync the next group.
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
        // Apply the connection's JS5 obfuscation: every outgoing byte is XORed with the client's
        // chosen key (control opcode 4). NopStreamCipher / key 0 leaves the bytes untouched.
        for (i in out.indices) out[i] = (out[i].toInt() xor (cipher.nextInt() and 0xFF)).toByte()
        return out
    }

    // A stored JS5 container is [compression:1][compressedLength:4][payload][optional version:2].
    // The client sizes each group from its header — compressedLength + 5 (uncompressed) or + 9
    // (compressed: 4 extra bytes for the decompressed length) — and reads exactly that, never the
    // 2-byte version trailer. Serve that exact slice so the block stream stays aligned. Index groups
    // (archive 255) have no trailer, so served == size and nothing is dropped.
    private fun servedBytes(c: ByteArray): ByteArray {
        if (c.size < 5) return c
        val compression = c[0].toInt() and 0xFF
        val compressedLength = ((c[1].toInt() and 0xFF) shl 24) or ((c[2].toInt() and 0xFF) shl 16) or
            ((c[3].toInt() and 0xFF) shl 8) or (c[4].toInt() and 0xFF)
        val served = (if (compression == 0) 5 else 9) + compressedLength
        return if (served in 1 until c.size) c.copyOf(served) else c
    }
}
