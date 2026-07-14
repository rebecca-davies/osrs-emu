package emu.protocol.osrs239.js5.message

import emu.netcore.message.IncomingMessage

data class Js5Request(val archive: Int, val group: Int, val prefetch: Boolean) : IncomingMessage
