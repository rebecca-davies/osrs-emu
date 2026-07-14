package emu.game.queue

/** Link stored directly on a queued request, matching the engine's mutable LinkList traversal. */
internal open class QueueLink {
    var previous: QueueLink? = null
    var next: QueueLink? = null
}

/**
 * Minimal intrusive FIFO used by the authentic queue implementations.
 *
 * Its cursor caches only the next link. Consequently an action appended behind an already-cached
 * successor can be observed later in the same pass, while an action appended behind the sole tail
 * waits for the next pass. LostCity explicitly preserves this original LinkList timing quirk.
 */
internal class IntrusiveQueue<T : QueueLink> {
    private val sentinel = QueueLink()

    var size: Int = 0
        private set

    init {
        sentinel.previous = sentinel
        sentinel.next = sentinel
    }

    fun addTail(link: T) {
        require(link.previous == null && link.next == null) { "request is already linked" }
        val tail = checkNotNull(sentinel.previous)
        link.previous = tail
        link.next = sentinel
        tail.next = link
        sentinel.previous = link
        size++
    }

    fun remove(link: T) {
        val previous = link.previous ?: return
        val next = checkNotNull(link.next)
        previous.next = next
        next.previous = previous
        link.previous = null
        link.next = null
        size--
    }

    fun clear() {
        while (true) {
            val head = sentinel.next
            if (head === sentinel) return
            @Suppress("UNCHECKED_CAST")
            remove(head as T)
        }
    }

    fun any(predicate: (T) -> Boolean): Boolean {
        val cursor = cursor()
        while (true) {
            val link = cursor.next() ?: return false
            if (predicate(link)) return true
        }
    }

    fun all(predicate: (T) -> Boolean): Boolean {
        val cursor = cursor()
        while (true) {
            val link = cursor.next() ?: return true
            if (!predicate(link)) return false
        }
    }

    fun cursor(): Cursor<T> = Cursor(sentinel)

    /** Live forward cursor whose next pointer has the same mutation behavior as LostCity. */
    class Cursor<T : QueueLink> internal constructor(private val sentinel: QueueLink) {
        private var next: QueueLink = checkNotNull(sentinel.next)

        fun next(): T? {
            val current = next
            if (current === sentinel) return null
            next = checkNotNull(current.next)
            @Suppress("UNCHECKED_CAST")
            return current as T
        }
    }
}
