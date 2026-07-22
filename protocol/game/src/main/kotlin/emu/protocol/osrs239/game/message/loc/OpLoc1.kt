package emu.protocol.osrs239.game.message.loc

import emu.transport.message.IncomingMessage

/** First menu operation selected on a loc at absolute ([x], [z]). */
data class OpLoc1(
    val type: Int,
    val x: Int,
    val z: Int,
    val subOption: Int,
    val keyCombination: Int,
) : IncomingMessage
