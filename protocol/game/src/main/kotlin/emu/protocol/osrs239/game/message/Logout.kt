package emu.protocol.osrs239.game.message

import emu.transport.message.OutgoingMessage

/** Requests a clean return to the rev-239 login screen. */
data object Logout : OutgoingMessage
