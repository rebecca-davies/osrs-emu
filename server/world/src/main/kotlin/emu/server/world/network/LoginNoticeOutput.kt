package emu.server.world.network

import emu.game.content.player.login.LoginNotice
import emu.protocol.osrs239.game.message.MessageGame

/** Converts game-owned login content into revision-239 messages. */
internal object LoginNoticeOutput {
    fun messages(notices: List<LoginNotice>): List<MessageGame> =
        notices.map { MessageGame(MessageGame.GAME_MESSAGE, it.text) }
}
