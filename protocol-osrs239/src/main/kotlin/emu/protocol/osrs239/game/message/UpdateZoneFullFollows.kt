package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Clears and selects one scene-local 8x8 zone for any zone payloads that would follow. */
data class UpdateZoneFullFollows(val zoneX: Int, val zoneZ: Int, val level: Int = 0) : OutgoingMessage
