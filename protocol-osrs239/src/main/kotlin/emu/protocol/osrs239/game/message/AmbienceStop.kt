package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Stops the current area ambience, optionally fading it out. */
data class AmbienceStop(val fade: Boolean) : OutgoingMessage
