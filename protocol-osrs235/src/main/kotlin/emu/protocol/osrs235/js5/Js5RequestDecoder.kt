package emu.protocol.osrs235.js5

import emu.buffer.JagexBuffer
import emu.netcore.codec.MessageDecoder
import emu.netcore.prot.Prot

class Js5RequestDecoder(private val prefetch: Boolean) : MessageDecoder<Js5Request> {
    override val prot: Prot = if (prefetch) Js5Prot.GROUP_REQUEST_PREFETCH else Js5Prot.GROUP_REQUEST
    override fun decode(buf: JagexBuffer): Js5Request {
        val archive = buf.readUByte()
        val group = buf.readUShort()
        return Js5Request(archive, group, prefetch)
    }
}
