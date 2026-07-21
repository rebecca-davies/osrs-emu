package emu.game.script.execution

/** A player script and content argument scheduled by a player queue or timer. */
data class PlayerScriptRequest(
    val script: PlayerScript,
    val argument: Any = Unit,
)
