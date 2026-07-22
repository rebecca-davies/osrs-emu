package emu.game.script.timer

import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner

/** Advances ready normal and soft player timers in their RuneScape phase order. */
class PlayerTimerRunner(
    private val scripts: PlayerScriptRunner,
    private val afterExecution: (Player) -> Unit = {},
) {
    fun run(player: Player) {
        if (player.loggingOut) return
        val worldTick = scripts.worldTick
        run(player, worldTick, soft = false)
        run(player, worldTick, soft = true)
    }

    private fun run(
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
                    scripts.start(
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
