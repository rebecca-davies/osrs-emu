package emu.protocol.osrs239.game.message.audio

import emu.transport.message.OutgoingMessage

/** Stops the current area ambience, optionally fading it out. */
data class AmbienceStop(val fade: Boolean) : OutgoingMessage
