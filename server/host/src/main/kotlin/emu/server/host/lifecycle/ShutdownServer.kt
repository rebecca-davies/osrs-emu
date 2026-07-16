package emu.server.host.lifecycle

import emu.server.game.GameService
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Stops bound server resources in dependency order even when the owning scope is cancelled. */
internal suspend fun shutdownServer(
    game: GameService,
    listener: AutoCloseable,
    gatewayJob: Job?,
    gameMonitor: Job?,
    shutdownHook: Thread? = null,
) {
    withContext(NonCancellable) {
        gameMonitor?.cancel()
        gatewayJob?.cancel()
        try {
            listener.close()
        } finally {
            try {
                gameMonitor?.join()
            } finally {
                try {
                    gatewayJob?.join()
                } finally {
                    shutdownHook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
                    game.stop()
                }
            }
        }
    }
}
