package emu.server.game.network.input.client

import emu.protocol.osrs239.game.message.client.NoTimeout
import emu.transport.pipeline.handler.HandlerContext
import emu.transport.pipeline.handler.MessageHandler

/** Accepts a keepalive after packet receipt has refreshed the reader's idle deadline. */
object NoTimeoutHandler : MessageHandler<NoTimeout> {
    override suspend fun handle(message: NoTimeout, ctx: HandlerContext) = Unit
}
