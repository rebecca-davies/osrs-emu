package emu.protocol.osrs239.game.message.client

import emu.transport.message.OutgoingMessage

/** Invokes a cache client script with integer and CP-1252 string arguments. */
data class RunClientScript(
    val id: Int,
    val arguments: List<Any> = emptyList(),
) : OutgoingMessage {
    init {
        require(arguments.all { it is Int || it is String }) {
            "client script arguments must be Int or String"
        }
    }
}
