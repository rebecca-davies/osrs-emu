package emu.protocol.osrs239.game.message.resumed

import emu.transport.message.IncomingMessage

/** Unsigned object type submitted through the active Jagex object dialog. */
data class ResumePObjDialog(val obj: Int) : IncomingMessage
