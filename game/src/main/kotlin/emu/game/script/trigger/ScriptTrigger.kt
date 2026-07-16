package emu.game.script.trigger

/** One global or subject-specific server-script lookup key. */
data class ScriptTrigger(
    val type: ServerTriggerType,
    val subject: Int? = null,
)
