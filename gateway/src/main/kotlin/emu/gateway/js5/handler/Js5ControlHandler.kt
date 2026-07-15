package emu.gateway.js5.handler

import emu.crypto.XorStreamCipher
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.prot.Js5Prot

/** Consumes JS5 controls and applies opcode 4's response key to the connection-local cipher. */
class Js5ControlHandler(private val cipher: XorStreamCipher) : PacketHandler<Js5Control> {
    override suspend fun handle(message: Js5Control, ctx: HandlerContext) {
        if (message.opcode == Js5Prot.CONTROL_XOR_KEY) cipher.key = message.b0
    }
}
