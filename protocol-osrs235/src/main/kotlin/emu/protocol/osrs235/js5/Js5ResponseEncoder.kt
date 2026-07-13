package emu.protocol.osrs235.js5

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot

object Js5ResponseEncoder : MessageEncoder<Js5GroupResponse> {
    override val prot: Prot = Js5Prot.GROUP_RESPONSE

    override fun encode(cipher: StreamCipher, message: Js5GroupResponse): ByteArray {
        val c = message.container
        val stream = ByteArray(3 + c.size)
        stream[0] = message.archive.toByte()
        stream[1] = (message.group ushr 8).toByte()
        stream[2] = message.group.toByte()
        c.copyInto(stream, 3)
        if (message.prefetch && c.isNotEmpty()) stream[3] = (stream[3].toInt() or 0x80).toByte()

        val out = ArrayList<Byte>(stream.size + stream.size / 511 + 1)
        var pos = 0; var block = 0
        while (pos < stream.size) {
            if (block > 0) out.add(0xFF.toByte())
            val take = minOf(if (block == 0) 512 else 511, stream.size - pos)
            for (k in 0 until take) out.add(stream[pos + k])
            pos += take; block++
        }
        return out.toByteArray()
    }
}
