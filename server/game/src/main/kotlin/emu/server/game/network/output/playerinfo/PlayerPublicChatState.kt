package emu.server.game.network.output.playerinfo

import emu.protocol.osrs239.game.message.chat.PlayerPublicChat

/** One public-chat update retained through the shared information phase, then cleared. */
internal class PlayerPublicChatState {
    private var publicChat: PlayerPublicChat? = null

    fun canPublish(): Boolean = publicChat == null

    fun publish(message: PlayerPublicChat) {
        check(publicChat == null) { "only one public message may be published per player cycle" }
        publicChat = message
    }

    fun current(): PlayerPublicChat? = publicChat

    fun clear() {
        publicChat = null
    }
}
