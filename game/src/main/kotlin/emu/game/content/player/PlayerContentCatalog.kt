package emu.game.content.player

import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoEditorScripts
import emu.game.content.areas.inferno.InfernoEquipmentScripts
import emu.game.content.areas.inferno.InfernoFreeModeScripts
import emu.game.content.areas.inferno.InfernoStatScripts
import emu.game.content.player.controls.PlayerControlsScripts
import emu.game.content.player.logout.LogoutScripts
import emu.game.content.ui.config.UiContent
import emu.game.obj.ObjCatalog
import emu.game.script.trigger.PlayerScriptRepository

/** Builds the immutable player-script index from feature-local Kotlin content. */
object PlayerContentCatalog {
    fun load(ui: UiContent, objs: ObjCatalog, inferno: InfernoArena): PlayerScriptRepository =
        PlayerScriptRepository.build(ui) {
            PlayerControlsScripts.register(this)
            LogoutScripts.register(this)
            InfernoFreeModeScripts.register(this, inferno)
            InfernoStatScripts.register(this)
            InfernoEquipmentScripts.register(this, objs)
            InfernoEditorScripts.register(this, inferno)
        }
}
