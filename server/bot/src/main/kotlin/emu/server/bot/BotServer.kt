package emu.server.bot

import emu.server.bot.config.BotConfig
import emu.server.bot.connection.BotConnection
import emu.server.bot.connection.BotEndpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

private val logger = KotlinLogging.logger {}

/** Owns the bounded request queue, worker lifetime, and all generated client connections. */
class BotServer(
    private val config: BotConfig,
    private val connection: BotConnection,
) : BotService {
    private val accepting = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val reservedClients = AtomicInteger()
    private val requests = Channel<LaunchRequest>(config.requestQueueCapacity)
    private lateinit var dispatcher: ExecutorCoroutineDispatcher
    private lateinit var selector: SelectorManager
    private lateinit var serverJob: Job

    override fun start(endpoint: BotEndpoint) {
        check(started.compareAndSet(false, true)) { "bot server can only be started once" }
        dispatcher = botDispatcher(config.workerThreads)
        try {
            selector = SelectorManager(dispatcher)
            val parent = SupervisorJob()
            val scope = CoroutineScope(parent + dispatcher)
            val loginPermits = Semaphore(config.maxConcurrentLogins)
            serverJob = parent
            scope.launch {
                for (request in requests) {
                    repeat(request.count) {
                        launchClient(endpoint, selector, loginPermits)
                    }
                }
            }
            accepting.set(true)
            logger.info { "bot server started with a hard limit of ${config.maxClients} clients" }
        } catch (failure: Throwable) {
            if (::selector.isInitialized) selector.close()
            dispatcher.close()
            throw failure
        }
    }

    override fun add(count: Int): BotLaunchResult {
        if (!accepting.get()) return BotLaunchResult.Unavailable
        if (count !in 1..config.maxPerRequest) return BotLaunchResult.InvalidCount(config.maxPerRequest)
        val total = reserve(count) ?: return BotLaunchResult.CapacityReached
        if (!requests.trySend(LaunchRequest(count)).isSuccess) {
            reservedClients.addAndGet(-count)
            return if (accepting.get()) BotLaunchResult.Busy else BotLaunchResult.Unavailable
        }
        return BotLaunchResult.Accepted(count, total)
    }

    override suspend fun stop() {
        if (!started.get() || !accepting.getAndSet(false)) return
        requests.close()
        serverJob.cancel()
        selector.close()
        serverJob.join()
        reservedClients.set(0)
        dispatcher.close()
        logger.info { "bot server stopped" }
    }

    private fun CoroutineScope.launchClient(
        endpoint: BotEndpoint,
        selector: SelectorManager,
        loginPermits: Semaphore,
    ) =
        launch {
            try {
                connection.run(endpoint, selector, loginPermits)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.debug(failure) { "headless bot client connection ended" }
            } finally {
                reservedClients.decrementAndGet()
            }
        }

    private fun reserve(count: Int): Int? {
        while (true) {
            val current = reservedClients.get()
            if (count > config.maxClients - current) return null
            val updated = current + count
            if (reservedClients.compareAndSet(current, updated)) return updated
        }
    }

    private data class LaunchRequest(val count: Int)
}

private fun botDispatcher(workerThreads: Int): ExecutorCoroutineDispatcher {
    val sequence = AtomicInteger()
    return Executors.newFixedThreadPool(workerThreads) { task ->
        Thread(task, "bot-client-${sequence.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
