package emu.gateway.js5

import emu.cache.store.Store
import emu.crypto.XorStreamCipher
import emu.netcore.message.IncomingMessage
import emu.netcore.pipeline.HandlerContext
import emu.netcore.pipeline.HandlerRepository
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.PacketHandler
import emu.protocol.osrs239.js5.Js5Control
import emu.protocol.osrs239.js5.Js5GroupResponse
import emu.protocol.osrs239.js5.Js5Prot
import emu.protocol.osrs239.js5.Js5Request

/**
 * Pure: maps a [Js5Request] to a [Js5GroupResponse] using the cache [Store]. No sockets, no framing.
 * Control frames ([Js5Control]) carry no response and are consumed silently so the group stream
 * keeps flowing. Control opcode `CONTROL_XOR_KEY` (4) sets this connection's response XOR key (its
 * `b0` byte), applied by the shared [cipher] that the pipeline also hands to the response encoder.
 *
 * A single instance is bound into a [emu.netcore.pipeline.HandlerRepository] under both
 * [Js5Request] and [Js5Control] (see `Main.kt`) — a temporary stand-in for the god-`when` this type
 * used to be called through directly, kept as one class until the per-packet split
 * (`Js5RequestHandler`/`Js5ControlHandler`) lands in a follow-up task.
 */
class Js5Handler(private val store: Store, private val cipher: XorStreamCipher) : PacketHandler<IncomingMessage> {
    override suspend fun handle(message: IncomingMessage, ctx: HandlerContext) {
        val req = when (message) {
            is Js5Request -> message
            is Js5Control -> {
                if (message.opcode == Js5Prot.CONTROL_XOR_KEY) cipher.key = message.b0
                return
            }
            else -> return
        }
        val container = store.read(req.archive, req.group) ?: return
        ctx.write(Js5GroupResponse(req.archive, req.group, container, req.prefetch))
    }
}

/**
 * Wraps a single [Js5Handler] instance into a [HandlerRepository] bound under both message types
 * it handles. Temporary: once [Js5Request]/[Js5Control] get their own per-packet handlers this
 * becomes an `installJs5Handlers` that binds each to its own small handler instead.
 */
fun Js5Handler.asHandlerRepository(): HandlerRepository = HandlerRepositoryBuilder()
    .bind(Js5Request::class.java, this)
    .bind(Js5Control::class.java, this)
    .build()
