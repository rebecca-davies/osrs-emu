package emu.server.host.config

import emu.persistence.postgres.database.PostgresConfig
import emu.persistence.postgres.database.PostgresOperationConfig
import emu.persistence.postgres.database.PostgresPoolConfig
import emu.server.bot.config.BotConfig
import emu.server.game.config.GameExecutionConfig
import emu.server.game.config.RouteSearchConfig
import emu.server.gateway.GatewayConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.auth.BcryptConfig
import emu.server.login.config.LoginExecutionConfig
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Builds validated service configuration from process environment values. */
fun loadServerConfig(environment: Map<String, String> = System.getenv()): ServerConfig {
    val gateway = environment.gatewayConfig()
    return ServerConfig(
        assets =
            RuntimeAssetConfig(
                cacheDirectory = File(environment["OSRS_CACHE_DIR"] ?: "cache-data"),
                rsaPropertiesFile =
                    File(environment["OSRS_SERVER_RSA_PROPERTIES"] ?: "server-rsa.properties"),
            ),
        gateway = gateway,
        login = environment.loginConfig(),
        js5 = environment.js5Config(),
        game = environment.gameConfig(),
        bots = environment.botConfig(),
        coordinator = environment.coordinatorConfig(),
        database = environment.postgresConfig(),
    )
}

private fun Map<String, String>.botConfig(): BotConfig =
    BotConfig().let { defaults ->
        val maxClients = int("OSRS_BOT_MAX_CLIENTS", defaults.maxClients)
        BotConfig(
            maxClients = maxClients,
            maxPerRequest = int("OSRS_BOT_MAX_PER_REQUEST", minOf(defaults.maxPerRequest, maxClients)),
            requestQueueCapacity = int("OSRS_BOT_REQUEST_QUEUE_CAPACITY", defaults.requestQueueCapacity),
            maxConcurrentLogins = int("OSRS_BOT_MAX_CONCURRENT_LOGINS", defaults.maxConcurrentLogins),
            workerThreads = int("OSRS_BOT_WORKER_THREADS", defaults.workerThreads),
            loginTimeout =
                long(
                    "OSRS_BOT_LOGIN_TIMEOUT_SECONDS",
                    defaults.loginTimeout.inWholeSeconds,
                ).seconds,
            keepAliveInterval =
                long(
                    "OSRS_BOT_KEEP_ALIVE_SECONDS",
                    defaults.keepAliveInterval.inWholeSeconds,
                ).seconds,
        )
    }

private fun Map<String, String>.gatewayConfig(): GatewayConfig =
    GatewayConfig().let { defaults ->
        GatewayConfig(
            bindHost = get("OSRS_GATEWAY_BIND_HOST") ?: defaults.bindHost,
            port = int("OSRS_GATEWAY_PORT", defaults.port),
            maxPendingClassifications =
                int(
                    "OSRS_GATEWAY_MAX_PENDING_CLASSIFICATIONS",
                    defaults.maxPendingClassifications,
                ),
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
            authentication =
                BcryptConfig(
                    int("OSRS_LOGIN_BCRYPT_COST", defaults.authentication.cost),
                ),
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

private fun Map<String, String>.gameConfig(): GameExecutionConfig =
    GameExecutionConfig().let { defaults ->
        val connection = defaults.connection
        val incomingActions = connection.incomingActions
        val collisionLoads = defaults.collisionLoads
        defaults.copy(
            connectionWorkerThreads =
                int("OSRS_GAME_CONNECTION_WORKER_THREADS", defaults.connectionWorkerThreads),
            entryWorkerThreads = int("OSRS_GAME_ENTRY_WORKER_THREADS", defaults.entryWorkerThreads),
            maxConcurrentSessions = int("OSRS_GAME_MAX_CONCURRENT_SESSIONS", defaults.maxConcurrentSessions),
            collisionLoads =
                collisionLoads.copy(
                    capacity =
                        int(
                            "OSRS_GAME_COLLISION_LOAD_QUEUE_CAPACITY",
                            collisionLoads.capacity,
                        ),
                    workerThreads =
                        int(
                            "OSRS_GAME_COLLISION_LOAD_WORKER_THREADS",
                            collisionLoads.workerThreads,
                        ),
                    shutdownTimeout =
                        long(
                            "OSRS_GAME_COLLISION_LOAD_SHUTDOWN_TIMEOUT_SECONDS",
                            collisionLoads.shutdownTimeout.inWholeSeconds,
                        ).seconds,
                ),
            routes =
                RouteSearchConfig(
                    maxPerCycle =
                        int(
                            "OSRS_GAME_ROUTE_SEARCH_LIMIT_PER_CYCLE",
                            defaults.routes.maxPerCycle,
                        ),
                ),
            commands =
                defaults.commands.copy(
                    capacity = int("OSRS_WORLD_COMMAND_QUEUE_CAPACITY", defaults.commands.capacity),
                    maxPerCycle =
                        int(
                            "OSRS_WORLD_COMMAND_LIMIT_PER_CYCLE",
                            defaults.commands.maxPerCycle,
                        ),
                ),
            connection =
                connection.copy(
                    idleTimeout =
                        long("OSRS_GAME_IDLE_TIMEOUT_SECONDS", connection.idleTimeout.inWholeSeconds).seconds,
                    incomingActions =
                        incomingActions.copy(
                            capacity =
                                int(
                                    "OSRS_GAME_INCOMING_ACTION_QUEUE_CAPACITY",
                                    incomingActions.capacity,
                                ),
                            maxPerCycle =
                                int(
                                    "OSRS_GAME_INCOMING_ACTION_LIMIT_PER_CYCLE",
                                    incomingActions.maxPerCycle,
                                ),
                        ),
                    outputQueueCapacity =
                        int("OSRS_GAME_OUTPUT_QUEUE_CAPACITY", connection.outputQueueCapacity),
                ),
        )
    }

private fun Map<String, String>.coordinatorConfig(): CoordinatorConfig =
    CoordinatorConfig().let { defaults ->
        defaults.copy(
            worldEntryTimeout =
                long(
                    "OSRS_COORDINATOR_WORLD_ENTRY_TIMEOUT_SECONDS",
                    defaults.worldEntryTimeout.inWholeSeconds,
                ).seconds,
        )
    }

private fun Map<String, String>.postgresConfig(): PostgresConfig =
    PostgresConfig(
        jdbcUrl = get("OSRS_DATABASE_URL") ?: "jdbc:postgresql://127.0.0.1:54330/osrsemu",
        username = get("OSRS_DATABASE_USER") ?: "osrsemu",
        password = get("OSRS_DATABASE_PASSWORD") ?: "osrsemu-dev",
        loginPool =
            postgresPoolConfig(
                prefix = "OSRS_DATABASE_LOGIN_POOL",
                defaults = PostgresPoolConfig(maximumSize = 4),
            ),
        worldPool =
            postgresPoolConfig(
                prefix = "OSRS_DATABASE_WORLD_POOL",
                defaults = PostgresPoolConfig(),
            ),
        operations = postgresOperationConfig(),
    )

private fun Map<String, String>.postgresOperationConfig(): PostgresOperationConfig =
    PostgresOperationConfig().let { defaults ->
        PostgresOperationConfig(
            connectTimeoutSeconds =
                int("OSRS_DATABASE_CONNECT_TIMEOUT_SECONDS", defaults.connectTimeoutSeconds),
            socketTimeoutSeconds =
                int("OSRS_DATABASE_SOCKET_TIMEOUT_SECONDS", defaults.socketTimeoutSeconds),
            statementTimeoutMillis =
                int("OSRS_DATABASE_STATEMENT_TIMEOUT_MS", defaults.statementTimeoutMillis),
        )
    }

private fun Map<String, String>.postgresPoolConfig(
    prefix: String,
    defaults: PostgresPoolConfig,
): PostgresPoolConfig =
    PostgresPoolConfig(
        maximumSize = int("${prefix}_MAXIMUM_SIZE", defaults.maximumSize),
        minimumIdle = int("${prefix}_MINIMUM_IDLE", defaults.minimumIdle),
        connectionTimeout =
            milliseconds("${prefix}_CONNECTION_TIMEOUT_MS", defaults.connectionTimeout),
        validationTimeout =
            milliseconds("${prefix}_VALIDATION_TIMEOUT_MS", defaults.validationTimeout),
        idleTimeout = milliseconds("${prefix}_IDLE_TIMEOUT_MS", defaults.idleTimeout),
        maxLifetime = milliseconds("${prefix}_MAX_LIFETIME_MS", defaults.maxLifetime),
    )

private fun Map<String, String>.int(name: String, default: Int): Int =
    get(name)?.let { value -> requireNotNull(value.toIntOrNull()) { "$name must be an integer" } } ?: default

private fun Map<String, String>.long(name: String, default: Long): Long =
    get(name)?.let { value -> requireNotNull(value.toLongOrNull()) { "$name must be an integer" } } ?: default

private fun Map<String, String>.milliseconds(name: String, default: Duration): Duration =
    get(name)?.let { value ->
        requireNotNull(value.toLongOrNull()) { "$name must be an integer number of milliseconds" }.milliseconds
    } ?: default
