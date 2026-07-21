package emu.protocol.osrs239.game.message.client

import emu.transport.message.IncomingMessage

/** Rev-239 applet focus transition reported by the client. */
data class EventAppletFocus(val focused: Boolean) : IncomingMessage
