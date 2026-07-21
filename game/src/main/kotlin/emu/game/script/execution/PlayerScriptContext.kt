package emu.game.script.execution

import emu.game.player.Player
import emu.game.script.queue.PlayerQueueDsl
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.ui.ButtonClick
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.suspendCoroutine

/** Receiver available to compiled Kotlin player content. */
@RestrictsSuspension
class PlayerScriptContext internal constructor(
    val player: Player,
    val lastButton: ButtonClick?,
    internal val argument: Any,
    internal val scripts: PlayerScriptRepository,
) : PlayerQueueDsl(player, scripts) {
    private var execution: PlayerScriptExecution? = null
    internal var worldTick: Long = 0
        private set

    /** Implements `P_DELAY`: resumes at the current world tick plus one plus [ticks]. */
    suspend fun delay(ticks: Int) {
        require(ticks != -1) { "script delay cannot use the RuneScript null sentinel" }
        suspendCoroutine<Unit> { continuation ->
            checkNotNull(execution) { "script delay used outside execution" }
                .delay(ticks, continuation)
        }
    }

    internal fun attach(execution: PlayerScriptExecution, worldTick: Long) {
        check(this.execution == null) { "script context is already executing" }
        require(worldTick >= 0) { "script world tick must be non-negative" }
        this.execution = execution
        this.worldTick = worldTick
    }

    internal fun detach(execution: PlayerScriptExecution) {
        check(this.execution === execution) { "another script owns this context" }
        this.execution = null
    }
}
