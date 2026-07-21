package emu.game.timer

import emu.game.script.execution.PlayerScriptRequest
import kotlin.reflect.KClass

/** Stable, typed identity for a named player timer script. */
data class PlayerTimerType<A : Any>(
    val name: String,
    val argumentType: KClass<A>,
) {
    init {
        require(name.isNotBlank()) { "a timer type name must not be blank" }
    }

    companion object {
        /** Creates a timer type whose script takes no content argument. */
        fun unit(name: String): PlayerTimerType<Unit> = PlayerTimerType(name, Unit::class)
    }
}

/** World-thread-owned recurring player timers. */
class PlayerTimers {
    private val timers = HashMap<PlayerTimerType<*>, ScheduledPlayerTimer>()
    private var nextSequence = 0L

    internal var first: ScheduledPlayerTimer? = null
        private set
    private var last: ScheduledPlayerTimer? = null
        private set

    internal val latestSequence: Long
        get() = nextSequence - 1

    /** Returns whether [type] currently has a normal or soft timer. */
    operator fun contains(type: PlayerTimerType<*>): Boolean = type in timers

    /** Clears [type] regardless of whether it is a normal or soft timer. */
    fun clear(type: PlayerTimerType<*>) {
        val timer = timers.remove(type) ?: return
        unlink(timer)
    }

    internal fun set(
        type: PlayerTimerType<*>,
        request: PlayerScriptRequest,
        intervalTicks: Int,
        worldTick: Long,
        soft: Boolean,
    ) {
        require(intervalTicks >= 0) { "timer interval must be non-negative" }
        val existing = timers[type]
        if (existing != null) {
            existing.request = request
            existing.intervalTicks = intervalTicks
            existing.clock = worldTick
            existing.soft = soft
            return
        }
        val timer =
            ScheduledPlayerTimer(
                type,
                request,
                intervalTicks,
                worldTick,
                soft,
                sequence = nextSequence++,
            )
        timers[type] = timer
        append(timer)
    }

    internal fun prepareRun(timer: ScheduledPlayerTimer, worldTick: Long): Boolean {
        if (timers[timer.type] !== timer) return false
        timer.clock = worldTick
        return true
    }

    private fun append(timer: ScheduledPlayerTimer) {
        timer.previous = last
        last?.next = timer
        if (first == null) first = timer
        last = timer
    }

    private fun unlink(timer: ScheduledPlayerTimer) {
        timer.previous?.next = timer.next
        timer.next?.previous = timer.previous
        if (first === timer) first = timer.next
        if (last === timer) last = timer.previous
    }
}

/** One recurring script timer in a player's deterministic traversal order. */
internal class ScheduledPlayerTimer(
    val type: PlayerTimerType<*>,
    var request: PlayerScriptRequest,
    var intervalTicks: Int,
    var clock: Long,
    var soft: Boolean,
    val sequence: Long,
) {
    var previous: ScheduledPlayerTimer? = null
    var next: ScheduledPlayerTimer? = null

    fun isReady(worldTick: Long): Boolean =
        worldTick >= clock && worldTick - clock >= intervalTicks
}
