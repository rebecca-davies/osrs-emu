package emu.game.script.queue

import emu.game.script.execution.PlayerScriptContext

/** Closes protected interfaces and clears weak actions for the current player. */
fun PlayerScriptContext.ifClose() {
    player.actionQueue.clearWeak()
    player.interfaces.closeModal()
}
