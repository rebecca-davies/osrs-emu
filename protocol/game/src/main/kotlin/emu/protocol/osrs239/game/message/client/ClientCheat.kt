package emu.protocol.osrs239.game.message.client

import emu.transport.message.IncomingMessage

/** Rev-239 developer-console input after the client removes its leading `::` marker. */
data class ClientCheat(val input: String) : IncomingMessage {
    init {
        require(input.length <= MAX_INPUT_LENGTH) { "CLIENT_CHEAT input exceeds $MAX_INPUT_LENGTH characters" }
    }

    companion object {
        const val MAX_INPUT_LENGTH = 80
    }
}
