package emu.buffer

/**
 * Big-endian, most-significant-bit-first bit writer, matching how the OSRS client unpacks
 * bit-packed wire data (the GPI-init local-coordinate/reference lists, player-info bitfields,
 * and similar structures). Bits are written most-significant-bit first within each byte and
 * bytes are emitted in order; the backing array grows on demand so callers do not need to
 * pre-size it for large writes (e.g. thousands of packed references).
 */
class BitBuf(initialBytes: Int = 16) {

    private var bytes = ByteArray(initialBytes)

    /** Total number of bits written so far. */
    var bitPosition: Int = 0
        private set

    /**
     * Appends the low [count] bits of [value], most-significant-bit first, to the stream.
     * Writes may straddle a byte boundary; the backing array grows automatically. [count] must
     * be in `1..32`. Returns `this` so calls can be chained.
     */
    fun writeBits(count: Int, value: Int): BitBuf {
        require(count in 1..32) { "count must be in 1..32, was $count" }

        ensureCapacity((bitPosition + count + 7) / 8)

        var bytePos = bitPosition ushr 3
        var bitOffset = 8 - (bitPosition and 7)
        var remaining = count

        while (remaining > bitOffset) {
            val mask = (1 shl bitOffset) - 1
            bytes[bytePos] = ((bytes[bytePos].toInt() and mask.inv()) or
                ((value ushr (remaining - bitOffset)) and mask)).toByte()
            bytePos++
            remaining -= bitOffset
            bitOffset = 8
        }

        val mask = (1 shl remaining) - 1
        val shift = bitOffset - remaining
        bytes[bytePos] = ((bytes[bytePos].toInt() and (mask shl shift).inv()) or
            ((value and mask) shl shift)).toByte()

        bitPosition += count
        return this
    }

    /**
     * Returns the bytes written so far. If the final byte is only partially filled, its
     * remaining low-order bits are zero.
     */
    fun toByteArray(): ByteArray = bytes.copyOf((bitPosition + 7) / 8)

    private fun ensureCapacity(requiredBytes: Int) {
        if (requiredBytes <= bytes.size) return
        var newSize = if (bytes.isEmpty()) 1 else bytes.size
        while (newSize < requiredBytes) newSize *= 2
        bytes = bytes.copyOf(newSize)
    }
}
