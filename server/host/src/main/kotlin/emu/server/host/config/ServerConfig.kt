package emu.server.host.config

import emu.persistence.postgres.database.PostgresConfig
import emu.server.game.config.GameExecutionConfig
import emu.server.gateway.GatewayConfig
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.login.config.LoginExecutionConfig

/** Process configuration loaded once by the server composition root. */
data class ServerConfig(
    val assets: RuntimeAssetConfig,
    val gateway: GatewayConfig,
    val login: LoginExecutionConfig,
    val js5: Js5ExecutionConfig,
    val game: GameExecutionConfig,
    val coordinator: CoordinatorConfig,
    val database: PostgresConfig
)
