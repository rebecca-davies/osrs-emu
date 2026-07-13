package emu.netcore.pipeline

import emu.netcore.message.IncomingMessage
import emu.netcore.message.OutgoingMessage

fun interface MessageHandler<I : IncomingMessage> {
    suspend fun handle(message: I, out: suspend (OutgoingMessage) -> Unit)
}
