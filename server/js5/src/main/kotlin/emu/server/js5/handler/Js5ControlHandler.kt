package emu.server.js5.handler

import emu.crypto.XorStreamCipher
import emu.protocol.osrs239.js5.message.Js5Control
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Applies JS5 control frames to the connection-local response XOR cipher without sending a reply. */
class Js5ControlHandler(private val cipher: XorStreamCipher) : MessageHandler<Js5Control> {
    override suspend fun handle(message: Js5Control, ctx: HandlerContext) {
        if (message.opcode == Js5Prot.CONTROL_XOR_KEY) cipher.key = message.b0
    }
}
