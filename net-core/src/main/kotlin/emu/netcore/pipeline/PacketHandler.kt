package emu.netcore.pipeline

import emu.netcore.message.IncomingMessage

/**
 * Handles exactly one decoded packet type. Each packet gets its own small [PacketHandler]
 * implementation (`Js5RequestHandler`, `WalkHandler`, …), bound into a [HandlerRepository] by its
 * message [Class] — never a shared `when(message){...)` that every new packet has to edit
 * (CLAUDE.md §5a).
 */
fun interface PacketHandler<in T : IncomingMessage> {
    suspend fun handle(message: T, ctx: HandlerContext)
}
