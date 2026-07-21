package emu.game.script.execution

import emu.game.player.Player
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick

/** Starts and resumes compiled Kotlin scripts only on the owning world thread. */
class PlayerScriptRunner(
    private val scripts: PlayerScriptRepository,
) {
    internal var worldTick = 0L
        private set

    /** Sets the authoritative tick used by every script started or resumed in this cycle. */
    fun beginCycle(worldTick: Long) {
        require(worldTick >= 0) { "script world tick must be non-negative" }
        this.worldTick = worldTick
    }

    /** Starts a server-trigger script when content registered the exact requested trigger. */
    fun trigger(
        player: Player,
        type: ServerTriggerType,
        subject: Int? = null,
        lastButton: ButtonClick? = null,
        protect: Boolean = true,
    ): Boolean {
        val script = scripts.findSpecific(type, subject) ?: return false
        return start(player, script, lastButton = lastButton, protect = protect)
    }

    /** Starts [script] with protected player access when available. */
    fun start(
        player: Player,
        script: PlayerScript,
        lastButton: ButtonClick? = null,
        argument: Any = Unit,
        protect: Boolean = true,
    ): Boolean {
        if (protect && !player.canAccess()) return false
        if (protect && player.shutdownAccess) player.discardActiveScript()
        val context = PlayerScriptContext(player, lastButton, argument, scripts)
        val execution = PlayerScriptExecution(script, context, protect)
        if (protect) player.isAccessProtected = true
        return try {
            execution.start(worldTick)
            retainIfSuspended(player, execution, protect)
            true
        } catch (failure: Throwable) {
            release(player, execution, protect)
            throw failure
        }
    }

    /** Advances the player's delayed active script by one player phase. */
    fun resume(player: Player) {
        val execution = player.activeScript ?: return
        try {
            execution.resumeCycle(worldTick)
            if (execution.isTerminal()) release(player, execution, execution.protectedAccess)
        } catch (failure: Throwable) {
            release(player, execution, execution.protectedAccess)
            throw failure
        }
    }

    /** Abandons a suspended script after a non-resumable lifecycle trigger. */
    fun discard(player: Player) = player.discardActiveScript()

    private fun retainIfSuspended(
        player: Player,
        execution: PlayerScriptExecution,
        protect: Boolean,
    ) {
        if (execution.isTerminal()) {
            release(player, execution, protect)
        } else {
            check(player.activeScript == null) { "player already has an active script" }
            player.activeScript = execution
            if (protect) player.isAccessProtected = true
        }
    }

    private fun release(
        player: Player,
        execution: PlayerScriptExecution,
        protectedAccess: Boolean,
    ) {
        if (player.activeScript === execution) player.activeScript = null
        if (protectedAccess) player.isAccessProtected = false
    }
}
