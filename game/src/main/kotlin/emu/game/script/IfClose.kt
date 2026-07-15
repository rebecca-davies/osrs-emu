package emu.game.script

/** Closes protected interfaces and clears weak actions for the current player. */
fun PlayerScriptContext.ifClose() {
    player.actionQueue.clearWeak()
    player.interfaces.closeModal()
}
