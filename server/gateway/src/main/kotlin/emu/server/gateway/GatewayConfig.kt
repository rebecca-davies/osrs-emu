package emu.server.gateway

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_PENDING_CLASSIFICATIONS = 4_096

/** Network endpoint and pending first-opcode limit for the public gateway. */
data class GatewayConfig(
    val bindHost: String = "0.0.0.0",
    val port: Int = DEFAULT_PORT,
    val maxPendingClassifications: Int = DEFAULT_PENDING_CLASSIFICATIONS,
    val classificationTimeout: Duration = 15.seconds,
) {
    init {
        require(bindHost.isNotBlank()) { "gateway bind host must not be blank" }
        require(port in 0..65535) { "gateway port must be between 0 and 65535" }
        require(maxPendingClassifications > 0) { "gateway classification limit must be positive" }
        require(classificationTimeout.isPositive()) { "gateway classification timeout must be positive" }
    }

    private companion object {
        const val DEFAULT_PORT = 43594
    }
}
