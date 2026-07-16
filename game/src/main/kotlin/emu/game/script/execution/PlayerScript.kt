package emu.game.script.execution

/** A named, compiled Kotlin content body executable by the deterministic player script runner. */
class PlayerScript internal constructor(
    val name: String,
    internal val body: suspend PlayerScriptContext.() -> Unit,
)
