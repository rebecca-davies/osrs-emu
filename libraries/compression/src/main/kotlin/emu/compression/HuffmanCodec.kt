package emu.compression

import java.nio.charset.Charset

/** Canonical Jagex Huffman codec built from the 256 code lengths in the cache `huffman` binary. */
class HuffmanCodec(codeLengths: ByteArray) {
    private val lengths = IntArray(SYMBOL_COUNT) { codeLengths[it].toInt() and 0xFF }
    private val codewords = IntArray(SYMBOL_COUNT)
    private var tree = IntArray(INITIAL_TREE_SIZE)
    private val maxBits: Int

    init {
        require(codeLengths.size == SYMBOL_COUNT) { "Huffman table must contain 256 code lengths" }
        val boundaries = IntArray(MAX_CODE_BITS + 1)
        var nextNode = 0
        for (symbol in lengths.indices) {
            val length = lengths[symbol]
            if (length == 0) continue
            require(length in 1..MAX_CODE_BITS) { "invalid Huffman code length $length" }
            val code = boundaries[length]
            codewords[symbol] = code

            val lengthBit = 1 shl (32 - length)
            val nextBoundary: Int
            if (code and lengthBit != 0) {
                nextBoundary = boundaries[length - 1]
            } else {
                nextBoundary = code or lengthBit
                for (shorter in length - 1 downTo 1) {
                    val boundary = boundaries[shorter]
                    if (boundary != code) break
                    val shorterBit = 1 shl (32 - shorter)
                    if (boundary and shorterBit != 0) {
                        boundaries[shorter] = boundaries[shorter - 1]
                        break
                    }
                    boundaries[shorter] = boundary or shorterBit
                }
            }
            boundaries[length] = nextBoundary
            for (longer in length + 1..MAX_CODE_BITS) {
                if (boundaries[longer] == code) boundaries[longer] = nextBoundary
            }

            var node = 0
            for (depth in 0 until length) {
                val mask = Int.MIN_VALUE ushr depth
                if (code and mask == 0) {
                    node++
                } else {
                    if (tree[node] == 0) tree[node] = nextNode
                    node = tree[node]
                }
                if (node >= tree.size) tree = tree.copyOf(tree.size * 2)
            }
            tree[node] = symbol.inv()
            nextNode = maxOf(nextNode, node + 1)
        }
        maxBits = lengths.max()
    }

    /** Encodes one CP-1252 string including the protocol's smart decoded-length prefix. */
    fun encode(text: String): ByteArray {
        val source = text.toByteArray(CP1252)
        require(source.size < 0x8000) { "Huffman text must be shorter than 32768 bytes" }
        val prefix = if (source.size < 0x80) 1 else 2
        val out = ByteArray(prefix + ((source.size * maxBits + 7) ushr 3))
        var offset = 0
        if (prefix == 1) {
            out[offset++] = source.size.toByte()
        } else {
            val encodedLength = source.size + 0x8000
            out[offset++] = (encodedLength ushr 8).toByte()
            out[offset++] = encodedLength.toByte()
        }
        var bitPosition = offset * 8
        for (byte in source) {
            val symbol = byte.toInt() and 0xFF
            val length = lengths[symbol]
            require(length != 0) { "CP-1252 symbol $symbol is absent from the Huffman table" }
            val code = codewords[symbol]
            for (bit in 0 until length) {
                if (code ushr (31 - bit) and 1 != 0) {
                    val byteIndex = bitPosition ushr 3
                    out[byteIndex] = (out[byteIndex].toInt() or (1 shl (7 - (bitPosition and 7)))).toByte()
                }
                bitPosition++
            }
        }
        return out.copyOf((bitPosition + 7) ushr 3)
    }

    /** Decodes one complete smart-length-prefixed payload with a caller-selected allocation cap. */
    fun decode(encoded: ByteArray, maxDecodedBytes: Int = DEFAULT_MAX_DECODED_BYTES): String {
        require(maxDecodedBytes >= 0) { "decoded Huffman limit cannot be negative" }
        require(encoded.isNotEmpty()) { "missing Huffman decoded-length prefix" }
        var offset = 0
        val first = encoded[offset++].toInt() and 0xFF
        val decodedLength =
            if (first < 0x80) first
            else {
                require(offset < encoded.size) { "truncated Huffman decoded-length prefix" }
                (((first shl 8) or (encoded[offset++].toInt() and 0xFF)) - 0x8000)
            }
        require(decodedLength <= maxDecodedBytes) { "decoded Huffman text exceeds $maxDecodedBytes bytes" }
        if (decodedLength == 0) return ""

        val result = ByteArray(decodedLength)
        var output = 0
        var node = 0
        var bitPosition = offset * 8
        val availableBits = encoded.size * 8
        while (output < decodedLength) {
            require(bitPosition < availableBits) { "truncated Huffman payload" }
            val bit = encoded[bitPosition ushr 3].toInt() ushr (7 - (bitPosition and 7)) and 1
            bitPosition++
            node = if (bit == 0) node + 1 else tree.getOrElse(node) { 0 }
            require(node in tree.indices) { "invalid Huffman code" }
            val value = tree[node]
            if (value < 0) {
                result[output++] = value.inv().toByte()
                node = 0
            }
        }
        return String(result, CP1252)
    }

    private companion object {
        const val SYMBOL_COUNT = 256
        const val MAX_CODE_BITS = 32
        const val INITIAL_TREE_SIZE = 8
        const val DEFAULT_MAX_DECODED_BYTES = 100
        val CP1252: Charset = Charset.forName("windows-1252")
    }
}
