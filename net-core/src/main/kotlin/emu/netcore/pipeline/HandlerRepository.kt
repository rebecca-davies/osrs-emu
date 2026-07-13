package emu.netcore.pipeline

import emu.netcore.message.IncomingMessage

/**
 * Type-keyed dispatch table that replaces a god `when(message){...}` handler (CLAUDE.md §5a):
 * every packet type is bound to its own [PacketHandler], so adding a packet never edits shared
 * dispatch code. A message type with no bound handler is silently dropped — the
 * [emu.netcore.codec.CodecRepository] already rejects unbound wire opcodes before a message is
 * ever decoded, so this only happens if a decoder is registered without a matching handler.
 */
class HandlerRepository internal constructor(
    private val byType: Map<Class<out IncomingMessage>, PacketHandler<*>>,
) {
    @Suppress("UNCHECKED_CAST")
    suspend fun dispatch(message: IncomingMessage, ctx: HandlerContext) {
        (byType[message.javaClass] as PacketHandler<IncomingMessage>?)?.handle(message, ctx)
    }
}

/** Builds an immutable [HandlerRepository]; rejects binding the same message type twice. */
class HandlerRepositoryBuilder {
    private val handlers = HashMap<Class<out IncomingMessage>, PacketHandler<*>>()

    fun <T : IncomingMessage> bind(type: Class<T>, handler: PacketHandler<T>): HandlerRepositoryBuilder {
        require(handlers.put(type, handler) == null) { "duplicate handler for $type" }
        return this
    }

    fun build(): HandlerRepository = HandlerRepository(handlers.toMap())
}
