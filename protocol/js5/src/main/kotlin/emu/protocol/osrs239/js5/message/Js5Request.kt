package emu.protocol.osrs239.js5.message

import emu.transport.message.IncomingMessage

/** One decoded JS5 archive and group request. */
data class Js5Request(val archive: Int, val group: Int, val prefetch: Boolean) : IncomingMessage
