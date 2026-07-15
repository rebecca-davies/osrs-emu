package emu.protocol.osrs239.js5.codec

import emu.buffer.JagexBuffer
import emu.transport.codec.MessageDecoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.js5.message.Js5Request
import emu.protocol.osrs239.js5.prot.Js5Prot

/** Decodes normal or prefetch JS5 group requests. */
class Js5RequestDecoder(private val prefetch: Boolean) : MessageDecoder<Js5Request> {
    override val prot: Prot = if (prefetch) Js5Prot.GROUP_REQUEST_PREFETCH else Js5Prot.GROUP_REQUEST
    override fun decode(buf: JagexBuffer): Js5Request {
        val archive = buf.readUByte()
        val group = buf.readUShort()
        return Js5Request(archive, group, prefetch)
    }
}
