package emu.gateway.js5

import emu.cache.store.Store
import emu.crypto.Js5XorCipher
import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage
import emu.netcore.pipeline.MessageHandler
import emu.protocol.osrs235.js5.Js5Control
import emu.protocol.osrs235.js5.Js5GroupResponse
import emu.protocol.osrs235.js5.Js5Request

// Pure: maps a Js5Request to a Js5GroupResponse using the cache Store. No sockets, no framing.
// Control frames (Js5Control) carry no response and are consumed silently so the group stream keeps
// flowing. Opcode 4 sets this connection's response XOR key, applied by the shared [cipher] that the
// pipeline also hands to the response encoder.
class Js5Handler(private val store: Store, private val cipher: Js5XorCipher) : MessageHandler<IncomingMessage> {
    override suspend fun handle(message: IncomingMessage, out: suspend (OutgoingMessage) -> Unit) {
        val req = when (message) {
            is Js5Request -> message
            is Js5Control -> {
                if (message.opcode == 4) cipher.key = message.b0
                return
            }
            else -> return
        }
        val container = store.read(req.archive, req.group) ?: return
        out(Js5GroupResponse(req.archive, req.group, container, req.prefetch))
    }
}
