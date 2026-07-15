package emu.game.content.ui

/** Immutable revision-pinned UI names and initial interface state. */
data class UiContent(
    val components: UiComponentMap,
    val gameframe: Gameframe,
)
