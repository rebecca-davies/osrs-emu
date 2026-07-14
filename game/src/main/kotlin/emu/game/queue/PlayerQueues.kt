package emu.game.queue

/** LostCity/OSRS player script queue categories. */
enum class PlayerQueueType {
    NORMAL,
    LONG,
    ENGINE,
    WEAK,
    STRONG,
    SOFT,
}

/** Behavior of a long queue request when its player starts logging out. */
enum class LongQueueLogoutPolicy {
    ACCELERATE,
    DISCARD,
}

/**
 * Per-player primary, weak, and engine queues.
 *
 * Primary and weak delays count down even without player access; ready work waits at zero or below
 * until access returns. Strong work closes the modal and clears weak work before any queue runs.
 * Engine work is always enqueued at zero delay and drains later in the player phase.
 */
class PlayerQueues {
    private class Request(
        val type: PlayerQueueType,
        var remainingDelayTicks: Int,
        val logoutPolicy: LongQueueLogoutPolicy?,
        val action: suspend () -> Unit,
    ) : QueueLink()

    private val primary = IntrusiveQueue<Request>()
    private val weak = IntrusiveQueue<Request>()
    private val engine = IntrusiveQueue<Request>()

    val primarySize: Int
        get() = primary.size

    val weakSize: Int
        get() = weak.size

    val engineSize: Int
        get() = engine.size

    /** Enqueues a normal, weak, strong, or soft request with its content-supplied delay. */
    fun enqueue(
        type: PlayerQueueType = PlayerQueueType.NORMAL,
        delayTicks: Int = 0,
        action: suspend () -> Unit,
    ) {
        require(delayTicks >= 0) { "delay must be non-negative" }
        require(type != PlayerQueueType.LONG) { "use enqueueLong for logout semantics" }
        val request = Request(type, if (type == PlayerQueueType.ENGINE) 0 else delayTicks, null, action)
        when (type) {
            PlayerQueueType.ENGINE -> engine.addTail(request)
            PlayerQueueType.WEAK -> weak.addTail(request)
            else -> primary.addTail(request)
        }
    }

    /** Enqueues engine work at an unconditional zero delay. */
    fun enqueueEngine(action: suspend () -> Unit) {
        enqueue(PlayerQueueType.ENGINE, action = action)
    }

    /** Enqueues long-running content work with explicit logout behavior. */
    fun enqueueLong(
        delayTicks: Int,
        logoutPolicy: LongQueueLogoutPolicy,
        action: suspend () -> Unit,
    ) {
        require(delayTicks >= 0) { "delay must be non-negative" }
        primary.addTail(Request(PlayerQueueType.LONG, delayTicks, logoutPolicy, action))
    }

    /**
     * Runs strong-modal handling, the primary queue, then the weak queue.
     *
     * [canAccess] is evaluated for every ready request because an earlier action may protect, delay,
     * or otherwise block the player for the remainder of the pass.
     */
    suspend fun processPrimaryAndWeak(
        canAccess: () -> Boolean,
        closeModal: suspend () -> Unit = {},
        loggingOut: Boolean = false,
    ) {
        if (primary.any { it.type == PlayerQueueType.STRONG }) {
            weak.clear()
            closeModal()
        }
        process(primary, canAccess, loggingOut)
        process(weak, canAccess, loggingOut = false)
    }

    /** Runs the engine queue at its later, dedicated point in the player phase. */
    suspend fun processEngine(canAccess: () -> Boolean) {
        process(engine, canAccess, loggingOut = false)
    }

    /** Clears transient weak work, as modal closure does in the authentic engine. */
    fun clearWeak() = weak.clear()

    /** Clears persistent primary work, primarily for player removal/reset. */
    fun clearPrimary() = primary.clear()

    /** Whether logout may proceed without waiting for primary content work. */
    fun isDiscardableForLogout(): Boolean =
        primary.all {
            it.type == PlayerQueueType.LONG && it.logoutPolicy == LongQueueLogoutPolicy.DISCARD
        }

    private suspend fun process(
        queue: IntrusiveQueue<Request>,
        canAccess: () -> Boolean,
        loggingOut: Boolean,
    ) {
        val cursor = queue.cursor()
        while (true) {
            val request = cursor.next() ?: return
            if (
                loggingOut &&
                request.type == PlayerQueueType.LONG &&
                request.logoutPolicy == LongQueueLogoutPolicy.ACCELERATE
            ) {
                request.remainingDelayTicks = 0
            }
            val previousDelay = request.remainingDelayTicks--
            if (previousDelay > 0 || !canAccess()) continue
            queue.remove(request)
            request.action()
        }
    }
}
