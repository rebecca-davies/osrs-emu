package emu.game.content.ui.config

import emu.game.content.ui.gameframe.Gameframe

/** Immutable revision-pinned UI names and initial interface state. */
data class UiContent(
    val components: UiComponentMap,
    val clientScripts: UiClientScriptMap,
    val clientConstants: UiClientConstantMap,
    val gameframe: Gameframe,
)
