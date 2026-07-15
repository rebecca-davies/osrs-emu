package emu.server.world.network

import emu.protocol.osrs239.game.message.PlayerPublicChat

/** One pending public-chat update consumed by the next player-info packet. */
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

    fun take(): PlayerPublicChat? = publicChat.also { publicChat = null }
}
