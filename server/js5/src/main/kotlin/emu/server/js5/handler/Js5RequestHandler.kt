package emu.server.js5.handler

import emu.cache.store.Store
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.message.Js5Request
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler

/**
 * Reads one requested group from [store] and emits its JS5 response through the protocol boundary.
 *
 * Missing groups emit no response because JS5 has no group-miss response frame. A request cannot
 * produce a log event, so hostile archive/group values cannot amplify process diagnostics.
 */
class Js5RequestHandler(private val store: Store) : PacketHandler<Js5Request> {
    override suspend fun handle(message: Js5Request, ctx: HandlerContext) {
        val container = store.read(message.archive, message.group)
        if (container == null) return
        ctx.write(Js5GroupResponse(message.archive, message.group, container, message.prefetch))
    }
}
