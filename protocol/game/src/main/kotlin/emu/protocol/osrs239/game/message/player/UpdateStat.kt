package emu.protocol.osrs239.game.message.player

import emu.transport.message.OutgoingMessage

/** Replaces one skill's current/invisible level and accumulated experience. */
data class UpdateStat(
    val stat: Int,
    val currentLevel: Int,
    val invisibleBoostedLevel: Int,
    val experience: Int,
) : OutgoingMessage
