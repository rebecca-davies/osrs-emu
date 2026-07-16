package emu.protocol.osrs239.game.message.component

import emu.transport.message.OutgoingMessage

/** Attaches [interfaceId] beneath a destination component in the active top-level frame. */
data class IfOpenSub(
    val destinationInterfaceId: Int,
    val destinationComponentId: Int,
    val interfaceId: Int,
    val type: Int = OVERLAY,
) : OutgoingMessage {
    companion object {
        /** Standard overlay subinterface type. */
        const val OVERLAY = 1

        /** Modal subinterface type. */
        const val MODAL = 0
    }
}
