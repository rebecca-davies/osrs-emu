package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Closes the subinterface tree attached beneath one destination component. */
data class IfCloseSub(
    val interfaceId: Int,
    val componentId: Int,
) : OutgoingMessage {
    init {
        require(interfaceId in UNSIGNED_SHORT) { "interface id must fit an unsigned short" }
        require(componentId in UNSIGNED_SHORT) { "component id must fit an unsigned short" }
    }

    private companion object {
        val UNSIGNED_SHORT = 0..0xFFFF
    }
}
