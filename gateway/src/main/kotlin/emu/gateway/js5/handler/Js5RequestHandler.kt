package emu.gateway.js5.handler

import emu.cache.store.Store
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.message.Js5Request
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Pure: maps a [Js5Request] to a [Js5GroupResponse] using the cache [store]. No sockets, no framing.
 *
 * When the requested archive/group isn't present it emits nothing — but this is a client-visible
 * hang: the OSRS client blocks its asset load waiting for that group and never completes (the
 * milestone-5 post-login freeze traced to the scene never finishing). So a miss is logged at WARN
 * with the exact archive:group, since a single unservable group the game-load needs is enough to
 * stall the whole login.
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
