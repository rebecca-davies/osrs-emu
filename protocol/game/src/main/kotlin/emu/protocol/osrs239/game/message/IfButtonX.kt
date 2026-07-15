package emu.protocol.osrs239.game.message

import emu.transport.message.IncomingMessage

/** Rev-239 IF_BUTTONX payload: packed component plus optional slot/object and one-based op. */
data class IfButtonX(
    val combinedId: Int,
    val sub: Int,
    val obj: Int,
    val op: Int,
) : IncomingMessage
