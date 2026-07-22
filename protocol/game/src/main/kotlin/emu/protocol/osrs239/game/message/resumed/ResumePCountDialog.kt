package emu.protocol.osrs239.game.message.resumed

import emu.transport.message.IncomingMessage

/** Integer submitted through the active Jagex count dialog. */
data class ResumePCountDialog(val count: Int) : IncomingMessage
