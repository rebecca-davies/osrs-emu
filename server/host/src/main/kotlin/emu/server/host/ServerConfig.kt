package emu.server.host

import emu.server.gateway.GatewayConfig
import emu.server.world.config.GameExecutionConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.config.LoginExecutionConfig
import emu.persistence.postgres.database.PostgresConfig

/** Process configuration loaded once by the server composition root. */
data class ServerConfig(
    val assets: RuntimeAssetConfig,
    val gateway: GatewayConfig,
    val login: LoginExecutionConfig,
    val js5: Js5ExecutionConfig,
    val world: GameExecutionConfig,
    val coordinator: CoordinatorConfig,
    val database: PostgresConfig
)
