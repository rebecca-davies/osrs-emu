package emu.protocol.osrs239.game.message

import emu.netcore.message.OutgoingMessage

/** Focuses the game camera on one player in the active world. */
data class CamTargetPlayer(val playerIndex: Int) : OutgoingMessage {
    init {
        require(playerIndex in 0 until 2048) { "player index out of range: $playerIndex" }
    }
}
