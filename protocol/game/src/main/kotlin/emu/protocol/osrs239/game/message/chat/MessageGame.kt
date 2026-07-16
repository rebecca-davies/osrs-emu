package emu.protocol.osrs239.game.message.chat

import emu.transport.message.OutgoingMessage

/**
 * A chatbox game message. [type] selects the rev-239 `chattype` (0 = `chattype_gamemessage`, the
 * plain server notice; others include public/private/clan/trade channels). [name] is only set for
 * request-style messages (e.g. "X wishes to trade with you.") where the client wires the line back
 * to an op-player interaction; a plain notice leaves it null.
 */
data class MessageGame(
    val type: Int,
    val text: String,
    val name: String? = null,
) : OutgoingMessage {
    companion object {
        /** `chattype_gamemessage`: the plain server notice line (e.g. "Welcome to RuneScape."). */
        const val GAME_MESSAGE = 0
    }
}
