package emu.game.script.timer

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner

/** Runs normal then soft timers and reports after each script advances at its phase boundary. */
class PlayerTimerProcess(
    private val runner: PlayerScriptRunner,
    private val afterExecution: (Player) -> Unit = {},
) {
    fun process(player: Player) {
        if (player.loggingOut) return
        val worldTick = runner.worldTick
        process(player, worldTick, soft = false)
        process(player, worldTick, soft = true)
    }

    private fun process(
        player: Player,
        worldTick: Long,
        soft: Boolean,
    ) {
        if (!soft && !player.canAccess()) return
        val timers = player.timers
        val latestSequence = timers.latestSequence
        var timer = timers.first
        while (timer != null) {
            val next = timer.next
            if (!soft && !player.canAccess()) return
            if (
                timer.sequence <= latestSequence &&
                    timer.soft == soft &&
                    timer.isReady(worldTick) &&
                    timers.prepareRun(timer, worldTick)
            ) {
                val started =
                    runner.start(
                        player,
                        timer.request.script,
                        argument = timer.request.argument,
                        protect = !soft,
                    )
                if (started) afterExecution(player)
            }
            timer = next
        }
    }
}
