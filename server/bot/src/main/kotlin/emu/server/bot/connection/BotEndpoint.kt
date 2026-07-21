package emu.server.bot.connection

import java.net.InetAddress

/** Resolved loopback endpoint of the gateway listener used by generated clients. */
data class BotEndpoint(
    val address: InetAddress,
    val port: Int,
) {
    init {
        require(address.isLoopbackAddress) { "bot clients may connect only to a loopback address" }
        require(port in 1..0xFFFF) { "bot client port must fit an unsigned short" }
    }
}
