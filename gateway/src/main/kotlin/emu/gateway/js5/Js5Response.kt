package emu.gateway.js5

object Js5Response {
    fun encode(archive: Int, group: Int, container: ByteArray, prefetch: Boolean): ByteArray {
        // Build the logical stream: 3-byte header + container (with prefetch bit on comp byte).
        val stream = ByteArray(3 + container.size)
        stream[0] = archive.toByte()
        stream[1] = (group ushr 8).toByte()
        stream[2] = group.toByte()
        container.copyInto(stream, 3)
        if (prefetch && container.isNotEmpty()) {
            stream[3] = (stream[3].toInt() or 0x80).toByte()
        }
        // Split into 512-byte on-wire blocks; every block after the first gets a 0xFF prefix.
        val out = ArrayList<Byte>(stream.size + stream.size / 511 + 1)
        var pos = 0
        var block = 0
        while (pos < stream.size) {
            if (block > 0) out.add(0xFF.toByte())
            val take = minOf(if (block == 0) 512 else 511, stream.size - pos)
            for (k in 0 until take) out.add(stream[pos + k])
            pos += take
            block++
        }
        return out.toByteArray()
    }
}
