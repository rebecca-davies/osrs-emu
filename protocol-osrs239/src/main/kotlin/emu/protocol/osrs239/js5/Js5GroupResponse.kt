package emu.protocol.osrs239.js5

import emu.netcore.message.OutgoingMessage

data class Js5GroupResponse(
    val archive: Int,
    val group: Int,
    val container: ByteArray,
    val prefetch: Boolean,
) : OutgoingMessage
