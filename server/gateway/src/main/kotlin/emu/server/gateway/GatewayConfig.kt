package emu.server.gateway

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_GATEWAY_PORT = 43594
private const val DEFAULT_GATEWAY_CONNECTIONS = 4_096

/** Network endpoint and accepted-connection limit for the public gateway. */
data class GatewayConfig(
    val bindHost: String = "0.0.0.0",
    val port: Int = DEFAULT_GATEWAY_PORT,
    val maxConnections: Int = DEFAULT_GATEWAY_CONNECTIONS,
    val classificationTimeout: Duration = 15.seconds,
) {
    init {
        require(bindHost.isNotBlank()) { "gateway bind host must not be blank" }
        require(port in 0..65535) { "gateway port must be between 0 and 65535" }
        require(maxConnections > 0) { "gateway connection limit must be positive" }
        require(classificationTimeout.isPositive()) { "gateway classification timeout must be positive" }
    }
}
