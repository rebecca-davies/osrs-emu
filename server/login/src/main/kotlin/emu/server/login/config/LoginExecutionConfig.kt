package emu.server.login.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_LOGIN_WORKERS = 4
private const val DEFAULT_LOGIN_ATTEMPTS = 32

/** Bounded worker and attempt limits for RSA parsing and password verification. */
data class LoginExecutionConfig(
    val workerThreads: Int = DEFAULT_LOGIN_WORKERS,
    val maxConcurrentAttempts: Int = DEFAULT_LOGIN_ATTEMPTS,
    val authenticationTimeout: Duration = 15.seconds,
) {
    init {
        require(workerThreads > 0) { "login worker count must be positive" }
        require(maxConcurrentAttempts > 0) { "login attempt limit must be positive" }
        require(authenticationTimeout.isPositive()) { "login authentication timeout must be positive" }
    }
}
