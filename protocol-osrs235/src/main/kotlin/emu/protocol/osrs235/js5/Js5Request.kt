package emu.protocol.osrs235.js5

import emu.netcore.message.IncomingMessage

data class Js5Request(val archive: Int, val group: Int, val prefetch: Boolean) : IncomingMessage
