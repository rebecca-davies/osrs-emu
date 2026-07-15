package emu.server.host

import emu.server.gateway.GatewayConfig
import emu.server.world.config.GameExecutionConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.auth.BcryptConfig
import emu.server.login.config.LoginExecutionConfig
import emu.persistence.postgres.database.PostgresConfig
import java.io.File

/** Process configuration loaded once by the server composition root. */
data class ServerConfig(
    val cacheDirectory: File,
    val rsaPropertiesFile: File,
    val gateway: GatewayConfig,
    val login: LoginExecutionConfig,
    val authentication: BcryptConfig,
    val js5: Js5ExecutionConfig,
    val world: GameExecutionConfig,
    val coordinator: CoordinatorConfig,
    val database: PostgresConfig,
)
