package emu.server

import emu.server.gateway.GatewayConfig
import emu.server.game.config.GameExecutionConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.config.LoginExecutionConfig
import emu.persistence.PostgresConfig
import java.io.File
import kotlin.time.Duration.Companion.seconds

/** Process configuration loaded once by the server composition root. */
data class ServerConfig(
    val cacheDirectory: File,
    val rsaPropertiesFile: File,
    val gateway: GatewayConfig,
    val login: LoginExecutionConfig,
    val js5: Js5ExecutionConfig,
    val game: GameExecutionConfig,
    val coordinator: CoordinatorConfig,
    val database: PostgresConfig,
)

/** Loads process environment values into one typed configuration. */
fun loadServerConfig(environment: Map<String, String> = System.getenv()): ServerConfig =
    ServerConfig(
        cacheDirectory = File(environment["OSRS_CACHE_DIR"] ?: "cache-data"),
        rsaPropertiesFile = File(environment["OSRS_SERVER_RSA_PROPERTIES"] ?: "server-rsa.properties"),
        gateway =
            GatewayConfig(
                bindHost = environment["OSRS_GATEWAY_BIND_HOST"] ?: "0.0.0.0",
                port = environment.int("OSRS_GATEWAY_PORT", 43594),
                maxConnections = environment.int("OSRS_GATEWAY_MAX_CONNECTIONS", 4_096),
                classificationTimeout =
                    environment.long(
                        "OSRS_GATEWAY_CLASSIFICATION_TIMEOUT_SECONDS",
                        GatewayConfig().classificationTimeout.inWholeSeconds,
                    ).seconds,
            ),
        login = LoginExecutionConfig().let { defaults ->
            defaults.copy(
                workerThreads = environment.int("OSRS_LOGIN_WORKER_THREADS", defaults.workerThreads),
                maxConcurrentAttempts =
                    environment.int("OSRS_LOGIN_MAX_CONCURRENT_ATTEMPTS", defaults.maxConcurrentAttempts),
                authenticationTimeout =
                    environment.long(
                        "OSRS_LOGIN_AUTHENTICATION_TIMEOUT_SECONDS",
                        defaults.authenticationTimeout.inWholeSeconds,
                    ).seconds,
            )
        },
        js5 = Js5ExecutionConfig().let { defaults ->
            defaults.copy(
                workerThreads = environment.int("OSRS_JS5_WORKER_THREADS", defaults.workerThreads),
                maxConcurrentSessions =
                    environment.int("OSRS_JS5_MAX_CONCURRENT_SESSIONS", defaults.maxConcurrentSessions),
                handshakeTimeout =
                    environment.long(
                        "OSRS_JS5_HANDSHAKE_TIMEOUT_SECONDS",
                        defaults.handshakeTimeout.inWholeSeconds,
                    ).seconds,
                frameIdleTimeout =
                    environment.long(
                        "OSRS_JS5_IDLE_TIMEOUT_SECONDS",
                        defaults.frameIdleTimeout.inWholeSeconds,
                    ).seconds,
            )
        },
        game = GameExecutionConfig().let { defaults ->
            defaults.copy(
                ioWorkerThreads = environment.int("OSRS_GAME_IO_WORKER_THREADS", defaults.ioWorkerThreads),
                maxConcurrentSessions =
                    environment.int("OSRS_GAME_MAX_CONCURRENT_SESSIONS", defaults.maxConcurrentSessions),
                idleTimeout =
                    environment.long("OSRS_GAME_IDLE_TIMEOUT_SECONDS", defaults.idleTimeout.inWholeSeconds).seconds,
            )
        },
        coordinator = CoordinatorConfig().let { defaults ->
            defaults.copy(
                admissionTimeout =
                    environment.long(
                        "OSRS_COORDINATOR_ADMISSION_TIMEOUT_SECONDS",
                        defaults.admissionTimeout.inWholeSeconds,
                    ).seconds,
            )
        },
        database = PostgresConfig.fromEnvironment(environment),
    )

private fun Map<String, String>.int(name: String, default: Int): Int =
    get(name)?.toIntOrNull() ?: if (containsKey(name)) error("$name must be an integer") else default

private fun Map<String, String>.long(name: String, default: Long): Long =
    get(name)?.toLongOrNull() ?: if (containsKey(name)) error("$name must be an integer") else default
