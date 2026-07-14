package emu.gateway.js5.handler

import emu.cache.store.Store
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.message.Js5Request

/**
 * Pure: maps a [Js5Request] to a [Js5GroupResponse] using the cache [store]. No sockets, no
 * framing — emits nothing (rather than an error response) when the requested archive/group isn't
 * present, matching the original god-`when` `Js5Handler`'s behavior.
 */
class Js5RequestHandler(private val store: Store) : PacketHandler<Js5Request> {
    override suspend fun handle(message: Js5Request, ctx: HandlerContext) {
        val container = store.read(message.archive, message.group) ?: return
        ctx.write(Js5GroupResponse(message.archive, message.group, container, message.prefetch))
    }
}
