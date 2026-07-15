package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Sets whether one interface component and its descendants are hidden. */
data class IfSetHide(
    val interfaceId: Int,
    val componentId: Int,
    val hidden: Boolean,
) : OutgoingMessage
