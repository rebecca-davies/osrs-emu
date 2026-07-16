package emu.game.script.execution

/** A player script and optional content argument waiting in an action queue. */
data class PlayerScriptRequest(
    val script: PlayerScript,
    val argument: Any = Unit,
)
