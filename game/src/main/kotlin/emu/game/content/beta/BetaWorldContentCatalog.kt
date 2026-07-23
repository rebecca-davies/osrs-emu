package emu.game.content.beta

import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoEditorInterface
import emu.game.content.areas.inferno.InfernoEditorScripts
import emu.game.content.areas.inferno.InfernoFreeModeScripts
import emu.game.content.areas.inferno.InfernoLoadoutCatalog
import emu.game.content.areas.inferno.InfernoStatScripts
import emu.game.content.beta.inferno.InfernoSuppliesScripts
import emu.game.content.beta.supplies.TournamentSuppliesCatalog
import emu.game.content.beta.supplies.TournamentSuppliesScripts
import emu.game.content.player.controls.PlayerControlsScripts
import emu.game.content.player.logout.LogoutScripts
import emu.game.content.ui.config.UiContent
import emu.game.obj.NamedObjEnumCatalog
import emu.game.obj.ObjCatalog
import emu.game.script.trigger.PlayerScriptRepository

/** Composes beta-world capabilities and area-contributed presets into the player content index. */
object BetaWorldContentCatalog {
    fun load(
        ui: UiContent,
        inferno: InfernoArena,
        objs: ObjCatalog? = null,
        objEnums: NamedObjEnumCatalog = NamedObjEnumCatalog.EMPTY,
    ): PlayerScriptRepository {
        val editor = InfernoEditorInterface(ui.components, inferno.config.editorRoster)
        val supplies =
            objs?.let {
                TournamentSuppliesCatalog.load(
                    ui = ui,
                    objs = it,
                    enums = objEnums,
                    loadouts = InfernoLoadoutCatalog.load(it),
                )
            }
        return PlayerScriptRepository.build(ui) {
            PlayerControlsScripts.register(this)
            LogoutScripts.register(this)
            InfernoFreeModeScripts.register(this, inferno, editor)
            InfernoStatScripts.register(this)
            InfernoEditorScripts.register(this, inferno, editor)
            supplies?.let {
                TournamentSuppliesScripts.register(this, it)
                InfernoSuppliesScripts.register(this, inferno, editor, it)
            }
        }
    }
}
