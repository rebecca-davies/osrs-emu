package emu.server.host

import emu.server.world.GameService
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Stops bound server resources in dependency order even when the owning scope is cancelled. */
internal suspend fun shutdownServer(
    game: GameService,
    listener: AutoCloseable,
    gatewayJob: Job?,
    worldMonitor: Job?,
    shutdownHook: Thread?,
) {
    withContext(NonCancellable) {
        worldMonitor?.cancel()
        gatewayJob?.cancel()
        try {
            listener.close()
        } finally {
            try {
                worldMonitor?.join()
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
