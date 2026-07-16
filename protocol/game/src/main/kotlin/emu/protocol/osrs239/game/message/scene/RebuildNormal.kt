package emu.protocol.osrs239.game.message.scene

import emu.transport.message.OutgoingMessage

/**
 * Recentres the normal 13x13-zone scene after login.
 *
 * Unlike [RebuildLogin], this in-game form contains no player-info initialization prefix.
 */
data class RebuildNormal(
    val centreZoneX: Int,
    val centreZoneY: Int,
    val worldArea: Int = 0,
) : OutgoingMessage
