package emu.game.content.player.logout

import emu.game.script.content.PlayerContent
import emu.game.script.queue.ifClose

/** Login/logout content triggers owned by the game module. */
object LogoutScripts {
    /** Adds logout interface triggers to the content repository under construction. */
    fun register(content: PlayerContent) {
        content.onButton("logout:logout") {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            ifClose()
            player.requestLogout()
        }
    }
}
