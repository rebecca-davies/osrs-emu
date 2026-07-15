package emu.server.gateway

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

fun interface GatewayRouteHandler {
    suspend fun handle(read: ByteReadChannel, write: ByteWriteChannel)
}

/** First-opcode routes supplied by the server coordinator. */
data class GatewayRoutes(
    val js5Opcode: Int,
    val loginOpcode: Int,
    val js5: GatewayRouteHandler,
    val login: GatewayRouteHandler,
) {
    init {
        require(js5Opcode in 0..255 && loginOpcode in 0..255) { "gateway route opcodes must fit one byte" }
        require(js5Opcode != loginOpcode) { "gateway route opcodes must be distinct" }
    }
}
