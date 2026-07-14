package emu.game.queue

/**
 * Per-NPC AI queue.
 *
 * Unlike player and world queues, its countdown pauses while the NPC is inactive or delayed. A
 * zero-delay action is eligible on the first active, non-delayed NPC queue pass.
 */
class NpcQueue {
    private class Request(
        var remainingDelayTicks: Int,
        val action: suspend () -> Unit,
    ) : QueueLink()

    private val requests = IntrusiveQueue<Request>()

    val size: Int
        get() = requests.size

    /** Appends an AI queue action. */
    fun enqueue(delayTicks: Int = 0, action: suspend () -> Unit) {
        require(delayTicks >= 0) { "delay must be non-negative" }
        requests.addTail(Request(delayTicks, action))
    }

    /** Processes one NPC queue pass, re-reading lifecycle and script-delay state per request. */
    suspend fun process(isActive: () -> Boolean, isDelayed: () -> Boolean) {
        if (!isActive()) return
        val cursor = requests.cursor()
        while (true) {
            val request = cursor.next() ?: return
            if (!isDelayed()) request.remainingDelayTicks--
            if (isDelayed() || request.remainingDelayTicks > 0) continue
            requests.remove(request)
            request.action()
        }
    }
}
