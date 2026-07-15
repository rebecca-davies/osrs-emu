package emu.server.host

import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresPoolConfig
import emu.server.world.config.GameExecutionConfig
import emu.server.gateway.GatewayConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.auth.BcryptConfig
import emu.server.login.config.LoginExecutionConfig
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Loads process environment values into validated service configuration. */
fun loadServerConfig(environment: Map<String, String> = System.getenv()): ServerConfig =
    ServerConfig(
        cacheDirectory = File(environment["OSRS_CACHE_DIR"] ?: "cache-data"),
        rsaPropertiesFile = File(environment["OSRS_SERVER_RSA_PROPERTIES"] ?: "server-rsa.properties"),
        gateway = environment.gatewayConfig(),
        login = environment.loginConfig(),
        authentication = BcryptConfig(environment.int("OSRS_LOGIN_BCRYPT_COST", BcryptConfig().cost)),
        js5 = environment.js5Config(),
        world = environment.worldConfig(),
        coordinator = environment.coordinatorConfig(),
        database = environment.postgresConfig(),
    )

private fun Map<String, String>.gatewayConfig(): GatewayConfig =
    GatewayConfig().let { defaults ->
        GatewayConfig(
            bindHost = get("OSRS_GATEWAY_BIND_HOST") ?: "0.0.0.0",
            port = int("OSRS_GATEWAY_PORT", 43594),
            maxConnections = int("OSRS_GATEWAY_MAX_CONNECTIONS", 4_096),
            classificationTimeout =
                long(
                    "OSRS_GATEWAY_CLASSIFICATION_TIMEOUT_SECONDS",
                    defaults.classificationTimeout.inWholeSeconds,
                ).seconds,
        )
    }

private fun Map<String, String>.loginConfig(): LoginExecutionConfig =
    LoginExecutionConfig().let { defaults ->
        defaults.copy(
            workerThreads = int("OSRS_LOGIN_WORKER_THREADS", defaults.workerThreads),
            maxConcurrentAttempts = int("OSRS_LOGIN_MAX_CONCURRENT_ATTEMPTS", defaults.maxConcurrentAttempts),
            authenticationTimeout =
                long(
                    "OSRS_LOGIN_AUTHENTICATION_TIMEOUT_SECONDS",
                    defaults.authenticationTimeout.inWholeSeconds,
                ).seconds,
        )
    }

private fun Map<String, String>.js5Config(): Js5ExecutionConfig =
    Js5ExecutionConfig().let { defaults ->
        defaults.copy(
            workerThreads = int("OSRS_JS5_WORKER_THREADS", defaults.workerThreads),
            maxConcurrentSessions = int("OSRS_JS5_MAX_CONCURRENT_SESSIONS", defaults.maxConcurrentSessions),
            handshakeTimeout =
                long("OSRS_JS5_HANDSHAKE_TIMEOUT_SECONDS", defaults.handshakeTimeout.inWholeSeconds).seconds,
            frameIdleTimeout = long("OSRS_JS5_IDLE_TIMEOUT_SECONDS", defaults.frameIdleTimeout.inWholeSeconds).seconds,
        )
    }

private fun Map<String, String>.worldConfig(): GameExecutionConfig =
    GameExecutionConfig().let { defaults ->
        defaults.copy(
            ioWorkerThreads = int("OSRS_GAME_IO_WORKER_THREADS", defaults.ioWorkerThreads),
            maxConcurrentSessions = int("OSRS_GAME_MAX_CONCURRENT_SESSIONS", defaults.maxConcurrentSessions),
            idleTimeout = long("OSRS_GAME_IDLE_TIMEOUT_SECONDS", defaults.idleTimeout.inWholeSeconds).seconds,
        )
    }

private fun Map<String, String>.coordinatorConfig(): CoordinatorConfig =
    CoordinatorConfig().let { defaults ->
        defaults.copy(
            admissionTimeout =
                long(
                    "OSRS_COORDINATOR_ADMISSION_TIMEOUT_SECONDS",
                    defaults.admissionTimeout.inWholeSeconds,
                ).seconds,
        )
    }

private fun Map<String, String>.postgresConfig(): PostgresConfig =
    PostgresConfig(
        jdbcUrl = get("OSRS_DATABASE_URL") ?: "jdbc:postgresql://127.0.0.1:54330/osrsemu",
        username = get("OSRS_DATABASE_USER") ?: "osrsemu",
        password = get("OSRS_DATABASE_PASSWORD") ?: "osrsemu-dev",
        pool = postgresPoolConfig(),
    )

private fun Map<String, String>.postgresPoolConfig(): PostgresPoolConfig =
    PostgresPoolConfig().let { defaults ->
        PostgresPoolConfig(
            maximumSize = int("OSRS_DATABASE_POOL_MAXIMUM_SIZE", defaults.maximumSize),
            minimumIdle = int("OSRS_DATABASE_POOL_MINIMUM_IDLE", defaults.minimumIdle),
            connectionTimeout = milliseconds("OSRS_DATABASE_POOL_CONNECTION_TIMEOUT_MS", defaults.connectionTimeout),
            validationTimeout = milliseconds("OSRS_DATABASE_POOL_VALIDATION_TIMEOUT_MS", defaults.validationTimeout),
            idleTimeout = milliseconds("OSRS_DATABASE_POOL_IDLE_TIMEOUT_MS", defaults.idleTimeout),
            maxLifetime = milliseconds("OSRS_DATABASE_POOL_MAX_LIFETIME_MS", defaults.maxLifetime),
        )
    }

private fun Map<String, String>.int(name: String, default: Int): Int =
    get(name)?.let { value -> requireNotNull(value.toIntOrNull()) { "$name must be an integer" } } ?: default

private fun Map<String, String>.long(name: String, default: Long): Long =
    get(name)?.let { value -> requireNotNull(value.toLongOrNull()) { "$name must be an integer" } } ?: default

private fun Map<String, String>.milliseconds(name: String, default: Duration): Duration =
    get(name)?.let { value ->
        requireNotNull(value.toLongOrNull()) { "$name must be an integer number of milliseconds" }.milliseconds
    } ?: default
