package emu.game.script.timer

import emu.game.script.execution.PlayerScriptContext
import emu.game.script.execution.PlayerScriptRequest
import emu.game.timer.PlayerTimerType

/** Starts or resets a normal timer from the current execution tick. */
fun PlayerScriptContext.setTimer(type: PlayerTimerType<Unit>, intervalTicks: Int) {
    setTimer(type, Unit, intervalTicks)
}

/** Starts or resets a normal timer carrying [argument] from the current execution tick. */
fun <A : Any> PlayerScriptContext.setTimer(
    type: PlayerTimerType<A>,
    argument: A,
    intervalTicks: Int,
) {
    scheduleTimer(type, argument, intervalTicks, soft = false)
}

/** Starts or resets a soft timer from the current execution tick. */
fun PlayerScriptContext.softTimer(type: PlayerTimerType<Unit>, intervalTicks: Int) {
    softTimer(type, Unit, intervalTicks)
}

/** Starts or resets a soft timer carrying [argument] from the current execution tick. */
fun <A : Any> PlayerScriptContext.softTimer(
    type: PlayerTimerType<A>,
    argument: A,
    intervalTicks: Int,
) {
    scheduleTimer(type, argument, intervalTicks, soft = true)
}

/** Clears [type]; clearing a missing timer has no effect. */
fun PlayerScriptContext.clearTimer(type: PlayerTimerType<*>) {
    player.timers.clear(type)
}

/** Clears [type]; clearing a missing timer has no effect. */
fun PlayerScriptContext.clearSoftTimer(type: PlayerTimerType<*>) {
    player.timers.clear(type)
}

private fun <A : Any> PlayerScriptContext.scheduleTimer(
    type: PlayerTimerType<A>,
    argument: A,
    intervalTicks: Int,
    soft: Boolean,
) {
    val script = scripts.requireTimer(type, soft)
    player.timers.set(type, PlayerScriptRequest(script, argument), intervalTicks, worldTick, soft)
}
