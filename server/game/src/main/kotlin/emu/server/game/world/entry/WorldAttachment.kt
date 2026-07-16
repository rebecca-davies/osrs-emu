package emu.server.game.world.entry

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Initial world-login and removal signals owned by one attached game connection. */
internal class WorldAttachment {
    private val attachedSignal = CompletableDeferred<WorldLogin?>()
    private val removedSignal = CompletableDeferred<Unit>()

    val login: Deferred<WorldLogin?> = attachedSignal
    val removed: Deferred<Unit> = removedSignal

    fun attach(login: WorldLogin) {
        attachedSignal.complete(login)
    }

    fun reject() {
        attachedSignal.complete(null)
        removedSignal.complete(Unit)
    }

    fun remove() {
        removedSignal.complete(Unit)
    }
}
