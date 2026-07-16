package emu.game.script.execution

/** Lifecycle of one compiled Kotlin player script invocation. */
enum class PlayerScriptExecutionState {
    READY,
    RUNNING,
    DELAYED,
    FINISHED,
    FAILED,
}
