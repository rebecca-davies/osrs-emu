package emu.game.content.player.controls

import emu.game.content.player.PlayerVarpCatalog
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptContext
import emu.game.script.queue.ifClose

/** Player-controls content triggers, including both client run-toggle components. */
object PlayerControlsScripts {
    /** Adds player-control triggers to the content repository under construction. */
    fun register(content: PlayerContent) {
        content.onButton("orbs:runbutton", "settings_side:runmode") {
            if (lastButton?.isPrimaryComponentClick != true) return@onButton
            ifClose()
            toggleRun()
        }
    }

    private fun PlayerScriptContext.toggleRun() {
        val enabled = player.varps[PlayerVarpCatalog.RUN_MODE] != 1
        player.varps[PlayerVarpCatalog.RUN_MODE] = if (enabled) 1 else 0
        player.movement.runEnabled = enabled
    }
}
