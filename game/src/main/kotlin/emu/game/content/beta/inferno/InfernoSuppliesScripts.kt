package emu.game.content.beta.inferno

import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoEditorInterface
import emu.game.content.areas.inferno.InfernoEditorLayout
import emu.game.content.beta.supplies.TournamentSupplies
import emu.game.script.content.PlayerContent

/** Opens the beta world's general supplies interface from the Inferno editor. */
internal object InfernoSuppliesScripts {
    fun register(
        content: PlayerContent,
        arena: InfernoArena,
        editor: InfernoEditorInterface,
        supplies: TournamentSupplies,
    ) {
        content.onButton(InfernoEditorLayout.gearCard.button) {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            if (!arena.isActive(player)) {
                editor.close(player)
                player.messageGame("Enter the Inferno before choosing beta equipment.")
                return@onButton
            }
            editor.closeControls(player)
            supplies.open(player)
        }
    }
}
