package emu.protocol.osrs239.game.message

import emu.transport.message.IncomingMessage

/** Client request to route to absolute tile ([x], [z]) with its held-key combination. */
data class MoveGameClick(
    val x: Int,
    val z: Int,
    val keyCombination: Int,
) : IncomingMessage
