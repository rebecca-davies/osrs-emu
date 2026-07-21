package emu.game.script.execution

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

/** Resumable state for one compiled Kotlin player script invocation. */
class PlayerScriptExecution internal constructor(
    private val script: PlayerScript,
    private val context: PlayerScriptContext,
    internal val protectedAccess: Boolean,
) {
    var state: PlayerScriptExecutionState = PlayerScriptExecutionState.READY
        private set

    private var executingWorldTick = 0L
    private var resumeWorldTick = Long.MAX_VALUE
    private var continuation: Continuation<Unit>? = null
    private var failure: Throwable? = null

    internal fun start(worldTick: Long) {
        check(state == PlayerScriptExecutionState.READY) { "script has already started" }
        executingWorldTick = worldTick
        state = PlayerScriptExecutionState.RUNNING
        execute(worldTick) { script.body.startCoroutine(context, completion) }
        throwFailure()
    }

    internal fun resumeCycle(worldTick: Long) {
        if (state != PlayerScriptExecutionState.DELAYED) return
        if (worldTick < resumeWorldTick) return
        val next = checkNotNull(continuation)
        continuation = null
        executingWorldTick = worldTick
        state = PlayerScriptExecutionState.RUNNING
        execute(worldTick) { next.resume(Unit) }
        throwFailure()
    }

    internal fun delay(ticks: Int, continuation: Continuation<Unit>) {
        check(state == PlayerScriptExecutionState.RUNNING) { "only a running script may delay" }
        check(this.continuation == null) { "script already has a continuation" }
        resumeWorldTick =
            Math.addExact(
                Math.addExact(executingWorldTick, 1L),
                ticks.toLong(),
            )
        this.continuation = continuation
        state = PlayerScriptExecutionState.DELAYED
    }

    internal fun isTerminal(): Boolean =
        state == PlayerScriptExecutionState.FINISHED || state == PlayerScriptExecutionState.FAILED

    internal fun discard() {
        continuation = null
        state = PlayerScriptExecutionState.FINISHED
    }

    private fun execute(worldTick: Long, action: () -> Unit) {
        context.attach(this, worldTick)
        try {
            action()
        } finally {
            context.detach(this)
        }
    }

    private fun throwFailure() {
        failure?.let { throw it }
    }

    private val completion =
        object : Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
                result.fold(
                    onSuccess = { state = PlayerScriptExecutionState.FINISHED },
                    onFailure = {
                        failure = it
                        state = PlayerScriptExecutionState.FAILED
                    },
                )
            }
        }
}
