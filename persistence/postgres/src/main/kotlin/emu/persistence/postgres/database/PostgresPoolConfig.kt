package emu.persistence.postgres.database

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_MAXIMUM_SIZE = 10
private const val DEFAULT_MINIMUM_IDLE = 1
private val DEFAULT_CONNECTION_TIMEOUT = 5.seconds
private val DEFAULT_VALIDATION_TIMEOUT = 2.seconds
private val DEFAULT_IDLE_TIMEOUT = 10.minutes
private val DEFAULT_MAX_LIFETIME = 30.minutes
private val MINIMUM_ACQUISITION_TIMEOUT = 250.milliseconds
private val MINIMUM_IDLE_TIMEOUT = 10.seconds
private val MINIMUM_MAX_LIFETIME = 30.seconds

/** Bounded PostgreSQL pool sizing and acquisition/validation deadlines. */
data class PostgresPoolConfig(
    val maximumSize: Int = DEFAULT_MAXIMUM_SIZE,
    val minimumIdle: Int = DEFAULT_MINIMUM_IDLE,
    val connectionTimeout: Duration = DEFAULT_CONNECTION_TIMEOUT,
    val validationTimeout: Duration = DEFAULT_VALIDATION_TIMEOUT,
    val idleTimeout: Duration = DEFAULT_IDLE_TIMEOUT,
    val maxLifetime: Duration = DEFAULT_MAX_LIFETIME,
) {
    init {
        require(maximumSize > 0) { "PostgreSQL maximum pool size must be positive" }
        require(minimumIdle in 0..maximumSize) {
            "PostgreSQL minimum idle connections must be between zero and the maximum pool size"
        }
        require(connectionTimeout.isFinite() && connectionTimeout >= MINIMUM_ACQUISITION_TIMEOUT) {
            "PostgreSQL connection timeout must be at least $MINIMUM_ACQUISITION_TIMEOUT"
        }
        require(
            validationTimeout.isFinite() &&
                validationTimeout >= MINIMUM_ACQUISITION_TIMEOUT &&
                validationTimeout < connectionTimeout,
        ) {
            "PostgreSQL validation timeout must be at least $MINIMUM_ACQUISITION_TIMEOUT " +
                "and shorter than the connection timeout"
        }
        require(idleTimeout == Duration.ZERO || idleTimeout.isFinite() && idleTimeout >= MINIMUM_IDLE_TIMEOUT) {
            "PostgreSQL idle timeout must be zero or at least $MINIMUM_IDLE_TIMEOUT"
        }
        require(maxLifetime == Duration.ZERO || maxLifetime.isFinite() && maxLifetime >= MINIMUM_MAX_LIFETIME) {
            "PostgreSQL maximum connection lifetime must be zero or at least $MINIMUM_MAX_LIFETIME"
        }
    }
}
