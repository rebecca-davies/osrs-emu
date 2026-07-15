package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Canonical snapshot of the top-level interface and its currently attached subinterfaces. */
data class IfResync(val topLevelInterface: Int, val subInterfaces: List<IfOpenSub>) : OutgoingMessage
