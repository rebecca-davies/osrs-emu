package emu.server.bot.connection

import io.ktor.network.selector.SelectorManager
import kotlinx.coroutines.sync.Semaphore

/** Advances one generated client through localhost login and its game connection lifetime. */
fun interface BotConnection {
    suspend fun run(
        endpoint: BotEndpoint,
        selector: SelectorManager,
        loginPermits: Semaphore,
    )
}
