package emu.game.script.execution

import emu.game.map.Tile
import emu.game.player.Player
import emu.game.script.input.CountDialogInput
import emu.game.script.input.ObjDialogInput
import emu.game.script.input.PlayerScriptInput
import emu.game.script.input.TileInput
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

    /** Opens Jagex's number dialog and suspends until the player submits a value. */
    suspend fun numberDialog(title: String): Int {
        val open = scripts.clientScripts.require(COUNT_DIALOG_SCRIPT)
        val close = scripts.clientScripts.require(CLOSE_DIALOG_SCRIPT)
        val mode = scripts.clientConstants.require(COUNT_DIALOG_MODE)
        player.interfaces.runClientScript(open, title)
        return waitForInput<CountDialogInput> {
            player.interfaces.runClientScript(close, mode)
        }.count
    }

    /** Opens Jagex's searchable object dialog and suspends until the player selects a type. */
    suspend fun objDialog(
        title: String,
        stockMarketRestriction: Boolean = true,
        enumRestriction: Int = -1,
        showLastSearched: Boolean = false,
    ): Int {
        val open = scripts.clientScripts.require(OBJ_DIALOG_SCRIPT)
        val close = scripts.clientScripts.require(CLOSE_DIALOG_SCRIPT)
        val mode = scripts.clientConstants.require(OBJ_DIALOG_MODE)
        player.interfaces.runClientScript(
            open,
            title,
            if (stockMarketRestriction) 1 else 0,
            enumRestriction,
            if (showLastSearched) 1 else 0,
        )
        return waitForInput<ObjDialogInput> {
            player.interfaces.runClientScript(close, mode)
        }.obj
    }

    /** Suspends until the player selects one world tile with the walk flag. */
    suspend fun pickTile(prompt: String): Tile {
        require(prompt.isNotBlank()) { "tile prompt must not be blank" }
        player.messageGame(prompt)
        return waitForInput<TileInput> {
            player.messageGame("Tile selection cancelled.")
        }.tile
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

    private suspend inline fun <reified T : PlayerScriptInput> waitForInput(
        noinline onDiscard: () -> Unit,
    ): T =
        suspendCoroutine { continuation ->
            checkNotNull(execution) { "script input used outside execution" }
                .waitForInput(T::class.java, continuation, onDiscard)
        }

    private companion object {
        const val COUNT_DIALOG_SCRIPT = "meslayer:countdialog"
        const val OBJ_DIALOG_SCRIPT = "meslayer:objdialog"
        const val CLOSE_DIALOG_SCRIPT = "meslayer:close"
        const val COUNT_DIALOG_MODE = "meslayer_mode:countdialog"
        const val OBJ_DIALOG_MODE = "meslayer_mode:objdialog"
    }
}
