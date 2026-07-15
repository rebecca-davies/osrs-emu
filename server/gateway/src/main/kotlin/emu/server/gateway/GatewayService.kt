package emu.server.gateway

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers

/** Binds the shared JS5/login endpoint and creates its connection dispatcher. */
class GatewayService(
    private val config: GatewayConfig,
    private val routes: GatewayRoutes,
) {
    suspend fun bind(): GatewayListener {
        val selector = SelectorManager(Dispatchers.IO)
        return try {
            val socket = aSocket(selector).tcp().bind(InetSocketAddress(config.bindHost, config.port))
            GatewayListener(
                socket,
                selector,
                routes,
                config.maxPendingClassifications,
                config.classificationTimeout,
            )
        } catch (failure: Throwable) {
            selector.close()
            throw failure
        }
    }
}
