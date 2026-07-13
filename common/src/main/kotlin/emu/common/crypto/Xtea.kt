package emu.common.crypto

object Xtea {
    private const val DELTA = -0x61c88647 // 0x9E3779B9
    private const val ROUNDS = 32

    fun encrypt(data: IntArray, key: IntArray) {
        var i = 0
        while (i + 1 < data.size) {
            var v0 = data[i]
            var v1 = data[i + 1]
            var sum = 0
            repeat(ROUNDS) {
                v0 += (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
                sum += DELTA
                v1 += (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
            }
            data[i] = v0
            data[i + 1] = v1
            i += 2
        }
    }

    fun decrypt(data: IntArray, key: IntArray) {
        var i = 0
        while (i + 1 < data.size) {
            var v0 = data[i]
            var v1 = data[i + 1]
            var sum = DELTA * ROUNDS
            repeat(ROUNDS) {
                v1 -= (((v0 shl 4) xor (v0 ushr 5)) + v0) xor (sum + key[(sum ushr 11) and 3])
                sum -= DELTA
                v0 -= (((v1 shl 4) xor (v1 ushr 5)) + v1) xor (sum + key[sum and 3])
            }
            data[i] = v0
            data[i + 1] = v1
            i += 2
        }
    }

    fun encrypt(bytes: ByteArray, key: IntArray): ByteArray = process(bytes, key, ::encrypt)
    fun decrypt(bytes: ByteArray, key: IntArray): ByteArray = process(bytes, key, ::decrypt)

    private fun process(bytes: ByteArray, key: IntArray, op: (IntArray, IntArray) -> Unit): ByteArray {
        val blocks = bytes.size / 8
        if (blocks == 0) return bytes.copyOf()
        val ints = IntArray(blocks * 2)
        for (b in 0 until blocks) {
            val o = b * 8
            ints[b * 2] = ((bytes[o].toInt() and 0xFF) shl 24) or
                ((bytes[o + 1].toInt() and 0xFF) shl 16) or
                ((bytes[o + 2].toInt() and 0xFF) shl 8) or
                (bytes[o + 3].toInt() and 0xFF)
            ints[b * 2 + 1] = ((bytes[o + 4].toInt() and 0xFF) shl 24) or
                ((bytes[o + 5].toInt() and 0xFF) shl 16) or
                ((bytes[o + 6].toInt() and 0xFF) shl 8) or
                (bytes[o + 7].toInt() and 0xFF)
        }
        op(ints, key)
        val out = bytes.copyOf()
        for (b in 0 until blocks) {
            val o = b * 8
            out[o] = (ints[b * 2] ushr 24).toByte()
            out[o + 1] = (ints[b * 2] ushr 16).toByte()
            out[o + 2] = (ints[b * 2] ushr 8).toByte()
            out[o + 3] = ints[b * 2].toByte()
            out[o + 4] = (ints[b * 2 + 1] ushr 24).toByte()
            out[o + 5] = (ints[b * 2 + 1] ushr 16).toByte()
            out[o + 6] = (ints[b * 2 + 1] ushr 8).toByte()
            out[o + 7] = ints[b * 2 + 1].toByte()
        }
        return out
    }
}
