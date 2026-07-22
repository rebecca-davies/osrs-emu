package emu.game.script.execution

import emu.game.script.input.PlayerScriptInput
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
    private var delayContinuation: Continuation<Unit>? = null
    private var inputContinuation: Continuation<PlayerScriptInput>? = null
    private var inputType: Class<out PlayerScriptInput>? = null
    private var inputDiscard: (() -> Unit)? = null
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
        val next = checkNotNull(delayContinuation)
        delayContinuation = null
        executingWorldTick = worldTick
        state = PlayerScriptExecutionState.RUNNING
        execute(worldTick) { next.resume(Unit) }
        throwFailure()
    }

    internal fun delay(ticks: Int, continuation: Continuation<Unit>) {
        check(state == PlayerScriptExecutionState.RUNNING) { "only a running script may delay" }
        check(delayContinuation == null && inputContinuation == null && inputDiscard == null) {
            "script already has a continuation"
        }
        resumeWorldTick =
            Math.addExact(
                Math.addExact(executingWorldTick, 1L),
                ticks.toLong(),
            )
        delayContinuation = continuation
        state = PlayerScriptExecutionState.DELAYED
    }

    internal fun <T : PlayerScriptInput> waitForInput(
        type: Class<T>,
        continuation: Continuation<T>,
        onDiscard: () -> Unit,
    ) {
        check(state == PlayerScriptExecutionState.RUNNING) { "only a running script may await input" }
        check(delayContinuation == null && inputContinuation == null && inputDiscard == null) {
            "script already has a continuation"
        }
        inputType = type
        @Suppress("UNCHECKED_CAST")
        val typedContinuation = continuation as Continuation<PlayerScriptInput>
        inputContinuation = typedContinuation
        inputDiscard = onDiscard
        state = PlayerScriptExecutionState.WAITING_INPUT
    }

    internal fun resumeInput(worldTick: Long, input: PlayerScriptInput): Boolean {
        if (state != PlayerScriptExecutionState.WAITING_INPUT) return false
        if (inputType?.isInstance(input) != true) return false
        val next = checkNotNull(inputContinuation)
        inputContinuation = null
        inputType = null
        inputDiscard = null
        executingWorldTick = worldTick
        state = PlayerScriptExecutionState.RUNNING
        execute(worldTick) { next.resume(input) }
        throwFailure()
        return true
    }

    internal fun isTerminal(): Boolean =
        state == PlayerScriptExecutionState.FINISHED || state == PlayerScriptExecutionState.FAILED

    internal fun isWaitingForInput(): Boolean = state == PlayerScriptExecutionState.WAITING_INPUT

    internal fun discard() {
        delayContinuation = null
        inputContinuation = null
        inputType = null
        val discard = inputDiscard
        inputDiscard = null
        state = PlayerScriptExecutionState.FINISHED
        discard?.invoke()
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
