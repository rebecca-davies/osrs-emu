package emu.server.js5.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_JS5_WORKERS = 4
private const val DEFAULT_JS5_SESSIONS = 64

/** Bounded worker, connection, and handshake limits for cache-update sessions. */
data class Js5ExecutionConfig(
    val workerThreads: Int = DEFAULT_JS5_WORKERS,
    val maxConcurrentSessions: Int = DEFAULT_JS5_SESSIONS,
    val handshakeTimeout: Duration = 15.seconds,
    val frameIdleTimeout: Duration = 30.seconds,
) {
    init {
        require(workerThreads > 0) { "JS5 worker count must be positive" }
        require(maxConcurrentSessions > 0) { "JS5 session limit must be positive" }
        require(handshakeTimeout.isPositive()) { "JS5 handshake timeout must be positive" }
        require(frameIdleTimeout.isPositive()) { "JS5 frame idle timeout must be positive" }
    }
}
