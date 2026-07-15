package emu.gateway.js5.handler

import emu.cache.store.Store
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.message.Js5Request
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Emits a cached JS5 group. Missing groups are logged and produce no response. */
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
