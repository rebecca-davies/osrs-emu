package emu.server.gateway

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Serves a connection after first-opcode classification. */
fun interface GatewayRoute {
    suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel)
}

/** First-opcode routes supplied by the server coordinator. */
data class GatewayRoutes(
    val js5Opcode: Int,
    val loginOpcode: Int,
    val js5: GatewayRoute,
    val login: GatewayRoute,
) {
    init {
        require(js5Opcode in 0..255 && loginOpcode in 0..255) { "gateway route opcodes must fit one byte" }
        require(js5Opcode != loginOpcode) { "gateway route opcodes must be distinct" }
    }
}
