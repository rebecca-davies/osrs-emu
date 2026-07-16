package emu.game.content.player

import emu.game.content.player.controls.PlayerControlsScripts
import emu.game.content.player.logout.LogoutScripts
import emu.game.content.ui.config.UiComponentMap
import emu.game.script.trigger.PlayerScriptRepository

/** Builds the immutable player-script index from feature-local Kotlin content. */
object PlayerContentCatalog {
    fun load(components: UiComponentMap): PlayerScriptRepository =
        PlayerScriptRepository.build(components) {
            PlayerControlsScripts.register(this)
            LogoutScripts.register(this)
        }
}
