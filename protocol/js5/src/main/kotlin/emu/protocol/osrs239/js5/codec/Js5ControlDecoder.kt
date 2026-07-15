package emu.protocol.osrs239.js5.codec

import emu.buffer.JagexBuffer
import emu.netcore.codec.MessageDecoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.js5.message.Js5Control

/**
 * Decodes a fixed 4-byte JS5 control frame (1-byte opcode already consumed by the pipeline + this
 * 3-byte payload). One instance is bound per control opcode (2, 3, 4, 6, 7).
 */
class Js5ControlDecoder(opcode: Int) : MessageDecoder<Js5Control> {
    override val prot: Prot = Prot(opcode, 3)
    override fun decode(buf: JagexBuffer): Js5Control =
        Js5Control(prot.opcode, buf.readUByte(), buf.readUByte(), buf.readUByte())
}
