package emu.persistence

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    companion object {
        private const val DEFAULT_MAXIMUM_SIZE = 10
        private const val DEFAULT_MINIMUM_IDLE = 1
        private val DEFAULT_CONNECTION_TIMEOUT = 5.seconds
        private val DEFAULT_VALIDATION_TIMEOUT = 2.seconds
        private val DEFAULT_IDLE_TIMEOUT = 10.minutes
        private val DEFAULT_MAX_LIFETIME = 30.minutes
        private val MINIMUM_ACQUISITION_TIMEOUT = 250.milliseconds
        private val MINIMUM_IDLE_TIMEOUT = 10.seconds
        private val MINIMUM_MAX_LIFETIME = 30.seconds

        /** Loads optional pool overrides, expressed as integer sizes and millisecond durations. */
        fun fromEnvironment(environment: Map<String, String>): PostgresPoolConfig {
            val defaults = PostgresPoolConfig()
            return PostgresPoolConfig(
                maximumSize = environment.int(MAXIMUM_SIZE_ENV, defaults.maximumSize),
                minimumIdle = environment.int(MINIMUM_IDLE_ENV, defaults.minimumIdle),
                connectionTimeout = environment.milliseconds(CONNECTION_TIMEOUT_ENV, defaults.connectionTimeout),
                validationTimeout = environment.milliseconds(VALIDATION_TIMEOUT_ENV, defaults.validationTimeout),
                idleTimeout = environment.milliseconds(IDLE_TIMEOUT_ENV, defaults.idleTimeout),
                maxLifetime = environment.milliseconds(MAX_LIFETIME_ENV, defaults.maxLifetime),
            )
        }

        private const val MAXIMUM_SIZE_ENV = "OSRS_DATABASE_POOL_MAXIMUM_SIZE"
        private const val MINIMUM_IDLE_ENV = "OSRS_DATABASE_POOL_MINIMUM_IDLE"
        private const val CONNECTION_TIMEOUT_ENV = "OSRS_DATABASE_POOL_CONNECTION_TIMEOUT_MS"
        private const val VALIDATION_TIMEOUT_ENV = "OSRS_DATABASE_POOL_VALIDATION_TIMEOUT_MS"
        private const val IDLE_TIMEOUT_ENV = "OSRS_DATABASE_POOL_IDLE_TIMEOUT_MS"
        private const val MAX_LIFETIME_ENV = "OSRS_DATABASE_POOL_MAX_LIFETIME_MS"
    }
}

private fun Map<String, String>.int(name: String, default: Int): Int =
    get(name)?.let { value ->
        requireNotNull(value.toIntOrNull()) { "$name must be an integer" }
    } ?: default

private fun Map<String, String>.milliseconds(name: String, default: Duration): Duration =
    get(name)?.let { value ->
        requireNotNull(value.toLongOrNull()) { "$name must be an integer number of milliseconds" }.milliseconds
    } ?: default
