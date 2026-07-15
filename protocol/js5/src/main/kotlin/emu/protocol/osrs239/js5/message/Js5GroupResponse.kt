package emu.protocol.osrs239.js5.message

import emu.transport.message.OutgoingMessage

/** Raw cache container returned for one JS5 group request. */
data class Js5GroupResponse(
    val archive: Int,
    val group: Int,
    val container: ByteArray,
    val prefetch: Boolean,
) : OutgoingMessage
