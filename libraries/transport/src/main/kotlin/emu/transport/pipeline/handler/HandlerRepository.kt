package emu.transport.pipeline.handler

import emu.transport.message.IncomingMessage

/** Type-keyed incoming-message dispatch table. Messages without a bound handler are dropped. */
class HandlerRepository internal constructor(
    private val byType: Map<Class<out IncomingMessage>, MessageHandler<*>>,
) {
    @Suppress("UNCHECKED_CAST")
    suspend fun dispatch(message: IncomingMessage, ctx: HandlerContext) {
        (byType[message.javaClass] as MessageHandler<IncomingMessage>?)?.handle(message, ctx)
    }
}

/** Builds an immutable [HandlerRepository] while rejecting duplicate message bindings. */
class HandlerRepositoryBuilder {
    private val handlers = HashMap<Class<out IncomingMessage>, MessageHandler<*>>()

    fun <T : IncomingMessage> bind(
        type: Class<T>,
        handler: MessageHandler<T>,
    ): HandlerRepositoryBuilder {
        require(handlers.putIfAbsent(type, handler) == null) { "duplicate handler for $type" }
        return this
    }

    fun build(): HandlerRepository = HandlerRepository(handlers.toMap())
}
