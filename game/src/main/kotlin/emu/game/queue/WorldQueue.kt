package emu.game.queue

/**
 * World-scoped delayed work drained at the start of every cycle.
 *
 * LostCity stores `delay + 1` and post-decrements before its readiness check. Thus a requested
 * delay of zero leaves one complete intervening world cycle before execution.
 */
class WorldQueue {
    private class Request(
        var remainingDelayTicks: Int,
        val action: suspend () -> Unit,
    ) : QueueLink()

    private val requests = IntrusiveQueue<Request>()

    val size: Int
        get() = requests.size

    /** Adds [action] at the FIFO tail using RuneScript world-delay semantics. */
    fun enqueue(delayTicks: Int = 0, action: suspend () -> Unit) {
        require(delayTicks in 0 until Int.MAX_VALUE) { "delay must be non-negative and finite" }
        requests.addTail(Request(delayTicks + 1, action))
    }

    /** Counts down and executes every request ready in this world phase. */
    suspend fun process() {
        val cursor = requests.cursor()
        while (true) {
            val request = cursor.next() ?: return
            val previousDelay = request.remainingDelayTicks--
            if (previousDelay > 0) continue
            requests.remove(request)
            request.action()
        }
    }
}
