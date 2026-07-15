package emu.game.queue

/**
 * World-thread-owned RuneScape action queues for one player.
 *
 * Actions are values; execution is supplied by the world-scoped processor. Primary, weak, and
 * engine lanes retain their distinct interruption and execution rules.
 */
class PlayerActionQueue<A : Any> {
    private val primary = Lane<A>()
    private val weak = Lane<A>()
    private val engine = Lane<A>()

    val primarySize: Int
        get() = primary.size

    val weakSize: Int
        get() = weak.size

    val engineSize: Int
        get() = engine.size

    /** Adds a normal, strong, weak, or engine action with a content-supplied delay. */
    fun add(
        action: A,
        priority: PlayerActionPriority = PlayerActionPriority.NORMAL,
        delayTicks: Int = 0,
    ) {
        require(delayTicks != -1) { "delay cannot use the RuneScript null sentinel" }
        require(priority != PlayerActionPriority.LONG) { "use addLong for logout behaviour" }
        val delay = if (priority == PlayerActionPriority.ENGINE) 0 else delayTicks
        val entry = Entry(action, priority, delay)
        when (priority) {
            PlayerActionPriority.ENGINE -> engine.addTail(entry)
            PlayerActionPriority.WEAK -> weak.addTail(entry)
            else -> primary.addTail(entry)
        }
    }

    /** Adds a long action with the logout behaviour encoded by RuneScript content. */
    fun addLong(action: A, delayTicks: Int, logout: LongActionLogout) {
        primary.addTail(Entry(action, PlayerActionPriority.LONG, delayTicks, logout))
    }

    /**
     * Processes the primary lane and then the weak lane.
     *
     * A strong action closes the modal and clears existing weak work before either lane runs.
     */
    fun processPrimaryAndWeak(
        canAccess: () -> Boolean,
        closeModal: () -> Unit = {},
        loggingOut: Boolean = false,
        execute: (A) -> Unit,
    ) {
        if (primary.any { it.priority == PlayerActionPriority.STRONG }) {
            weak.clear()
            closeModal()
        }
        process(primary, canAccess, loggingOut, execute)
        process(weak, canAccess, loggingOut = false, execute)
    }

    /** Processes engine-generated actions at their later point in the player phase. */
    fun processEngine(canAccess: () -> Boolean, execute: (A) -> Unit) {
        val cursor = engine.cursor()
        while (true) {
            val entry = cursor.next() ?: return
            if (!canAccess()) continue
            execute(entry.action)
            engine.remove(entry)
        }
    }

    fun clearWeak() = weak.clear()

    fun clearPrimary() = primary.clear()

    fun clearEngine() = engine.clear()

    /** Removes matching primary and weak actions while leaving engine-generated work untouched. */
    fun removeAll(action: A): Int =
        primary.removeAll { it.action == action } + weak.removeAll { it.action == action }

    /** Removes matching engine-generated actions. */
    fun removeAllEngine(action: A): Int = engine.removeAll { it.action == action }

    /** Counts matching primary and weak actions; engine-generated work is deliberately excluded. */
    fun count(action: A): Int =
        primary.count { it.action == action } + weak.count { it.action == action }

    /** True when primary work cannot prevent logout; engine work is checked separately. */
    fun isDiscardableForLogout(): Boolean =
        primary.all {
            it.priority == PlayerActionPriority.LONG && it.logout == LongActionLogout.DISCARD
        }

    private fun process(
        lane: Lane<A>,
        canAccess: () -> Boolean,
        loggingOut: Boolean,
        execute: (A) -> Unit,
    ) {
        val cursor = lane.cursor()
        while (true) {
            val entry = cursor.next() ?: return
            if (
                loggingOut &&
                    entry.priority == PlayerActionPriority.LONG &&
                    entry.logout == LongActionLogout.ACCELERATE
            ) {
                entry.remainingDelayTicks = 0
            }
            val delay = entry.remainingDelayTicks
            entry.remainingDelayTicks = delay.saturatingDecrement()
            if (delay > 0 || !canAccess()) continue
            lane.remove(entry)
            execute(entry.action)
        }
    }

    private class Entry<A : Any>(
        val action: A,
        val priority: PlayerActionPriority,
        var remainingDelayTicks: Int,
        val logout: LongActionLogout? = null,
    ) {
        var previous: Entry<A>? = null
        var next: Entry<A>? = null
    }

    private class Lane<A : Any> {
        private var first: Entry<A>? = null
        private var last: Entry<A>? = null

        var size: Int = 0
            private set

        fun addTail(entry: Entry<A>) {
            check(entry.previous == null && entry.next == null) { "action is already queued" }
            entry.previous = last
            last?.next = entry
            if (first == null) first = entry
            last = entry
            size++
        }

        fun remove(entry: Entry<A>) {
            val previous = entry.previous
            val next = entry.next
            if (previous == null && first !== entry) return
            if (previous == null) first = next else previous.next = next
            if (next == null) last = previous else next.previous = previous
            entry.previous = null
            entry.next = null
            size--
        }

        fun clear() {
            var entry = first
            while (entry != null) {
                val next = entry.next
                entry.previous = null
                entry.next = null
                entry = next
            }
            first = null
            last = null
            size = 0
        }

        fun any(predicate: (Entry<A>) -> Boolean): Boolean {
            val cursor = cursor()
            while (true) {
                val entry = cursor.next() ?: return false
                if (predicate(entry)) return true
            }
        }

        fun all(predicate: (Entry<A>) -> Boolean): Boolean {
            val cursor = cursor()
            while (true) {
                val entry = cursor.next() ?: return true
                if (!predicate(entry)) return false
            }
        }

        fun removeAll(predicate: (Entry<A>) -> Boolean): Int {
            var removed = 0
            val cursor = cursor()
            while (true) {
                val entry = cursor.next() ?: return removed
                if (!predicate(entry)) continue
                remove(entry)
                removed++
            }
        }

        fun count(predicate: (Entry<A>) -> Boolean): Int {
            var count = 0
            val cursor = cursor()
            while (true) {
                val entry = cursor.next() ?: return count
                if (predicate(entry)) count++
            }
        }

        fun cursor(): Cursor<A> = Cursor(first)
    }

    /** Caches each successor before yielding so mutations cannot skip the next queued entry. */
    private class Cursor<A : Any>(first: Entry<A>?) {
        private var next = first

        fun next(): Entry<A>? {
            val current = next ?: return null
            next = current.next
            return current
        }
    }

    private fun Int.saturatingDecrement(): Int = if (this == Int.MIN_VALUE) this else this - 1
}
