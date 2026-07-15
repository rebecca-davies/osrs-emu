package emu.server.world.runtime

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin

/** Owns execution and termination reporting for the authoritative world coroutine. */
class WorldLifecycle(
    dispatcher: CoroutineDispatcher,
    private val runWorld: suspend () -> Unit,
) {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + dispatcher)
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private var execution: Deferred<Unit>? = null

    val isRunning: Boolean
        get() = execution?.isActive == true

    fun start() {
        check(!stopped.get()) { "world lifecycle has already stopped" }
        check(started.compareAndSet(false, true)) { "world lifecycle can only be started once" }
        execution = scope.async { runWorld() }
    }

    /** Suspends until the world stops and rethrows an unexpected world failure. */
    suspend fun awaitTermination() {
        check(started.get()) { "world lifecycle has not started" }
        requireNotNull(execution).await()
    }

    suspend fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        execution?.cancelAndJoin()
        supervisor.cancel()
    }
}
