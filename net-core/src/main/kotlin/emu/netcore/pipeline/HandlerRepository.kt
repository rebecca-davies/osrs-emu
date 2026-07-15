package emu.netcore.pipeline

import emu.netcore.message.IncomingMessage

/** Type-keyed packet dispatch table. Messages without a bound handler are dropped. */
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
