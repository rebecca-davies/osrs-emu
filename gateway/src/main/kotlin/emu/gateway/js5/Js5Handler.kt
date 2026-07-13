package emu.gateway.js5

import emu.cache.store.Store
import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage
import emu.netcore.pipeline.MessageHandler
import emu.protocol.osrs235.js5.Js5GroupResponse
import emu.protocol.osrs235.js5.Js5Request

// Pure: maps a Js5Request to a Js5GroupResponse using the cache Store. No sockets, no framing.
class Js5Handler(private val store: Store) : MessageHandler<IncomingMessage> {
    override suspend fun handle(message: IncomingMessage, out: suspend (OutgoingMessage) -> Unit) {
        val req = message as Js5Request
        val container = store.read(req.archive, req.group) ?: return
        out(Js5GroupResponse(req.archive, req.group, container, req.prefetch))
    }
}
