package emu.server.js5.handler

import emu.cache.store.Store
import emu.transport.pipeline.HandlerContext
import emu.transport.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.message.Js5Request
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Pure: maps a [Js5Request] to a [Js5GroupResponse] using the cache [store]. No sockets, no framing.
 *
 * Missing groups emit no response because JS5 has no group-miss response frame. Every miss is
 * logged with its archive and group because the client will keep waiting for required assets.
 */
class Js5RequestHandler(private val store: Store) : PacketHandler<Js5Request> {
    override suspend fun handle(message: Js5Request, ctx: HandlerContext) {
        val container = store.read(message.archive, message.group)
        if (container == null) {
            logger.warn { "JS5 miss: archive=${message.archive} group=${message.group} not in store — sending nothing, client will stall on this group" }
            return
        }
        ctx.write(Js5GroupResponse(message.archive, message.group, container, message.prefetch))
    }
}
