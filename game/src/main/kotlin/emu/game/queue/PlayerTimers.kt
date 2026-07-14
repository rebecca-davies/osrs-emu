package emu.game.queue

/** Access-gated normal timers and access-independent soft timers. */
enum class PlayerTimerType {
    NORMAL,
    SOFT,
}

/**
 * Repeating per-player timers keyed by content/script identifier.
 *
 * A due normal timer remains due while access is blocked. A soft timer ignores access. After
 * firing, the clock resets to the current tick, preventing a late timer from producing catch-up
 * bursts.
 */
class PlayerTimers {
    private class Timer(
        val id: Any,
        var type: PlayerTimerType,
        var intervalTicks: Int,
        var clock: Long,
        var action: suspend () -> Unit,
    ) : QueueLink()

    private val timers = IntrusiveQueue<Timer>()
    private val byId = mutableMapOf<Any, Timer>()

    val size: Int
        get() = timers.size

    /** Installs or replaces a timer without changing an existing timer's iteration position. */
    fun set(
        id: Any,
        type: PlayerTimerType,
        intervalTicks: Int,
        currentTick: Long,
        action: suspend () -> Unit,
    ) {
        require(intervalTicks >= 0) { "timer interval must be non-negative" }
        val existing = byId[id]
        if (existing != null) {
            existing.type = type
            existing.intervalTicks = intervalTicks
            existing.clock = currentTick
            existing.action = action
            return
        }
        val timer = Timer(id, type, intervalTicks, currentTick, action)
        byId[id] = timer
        timers.addTail(timer)
    }

    /** Removes the timer with [id], if present. */
    fun clear(id: Any) {
        val timer = byId.remove(id) ?: return
        timers.remove(timer)
    }

    /** Runs one timer category at its assigned point in the player phase. */
    suspend fun process(type: PlayerTimerType, currentTick: Long, canAccess: () -> Boolean) {
        val cursor = timers.cursor()
        while (true) {
            val timer = cursor.next() ?: return
            if (timer.type != type) continue
            if (currentTick < timer.clock + timer.intervalTicks) continue
            if (timer.type != PlayerTimerType.SOFT && !canAccess()) continue
            timer.clock = currentTick
            timer.action()
        }
    }
}
