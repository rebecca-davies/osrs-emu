package emu.server.host.composition

import emu.crypto.RsaPublicKey
import emu.server.bot.BotLaunchResult
import emu.server.bot.BotServer
import emu.server.bot.BotService
import emu.server.bot.config.BotConfig
import emu.server.bot.connection.BotConnection
import emu.server.bot.connection.BotConnectionRunner
import emu.server.bot.connection.BotEndpoint
import emu.server.game.world.player.cheat.BotClientRequestResult
import emu.server.game.world.player.cheat.BotClientRequestSink
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import org.koin.dsl.module

/** Composes localhost bot clients with the game server only through a host-owned adapter. */
internal fun botModule(config: BotConfig, publicKey: RsaPublicKey) = module {
    single<BotConnection> { BotConnectionRunner(config, publicKey) }
    single<BotService> { BotServer(config, get()) }
    single<BotClientRequestSink> {
        val bots = get<BotService>()
        BotClientRequestSink { count -> bots.add(count).toGameResult() }
    }
}

/** Selects a loopback route to the listener without allowing generated clients onto another network. */
internal fun InetSocketAddress.botEndpointOrNull(): BotEndpoint? {
    val boundAddress = address ?: return null
    val targetAddress =
        when {
            boundAddress.isLoopbackAddress -> boundAddress
            boundAddress.isAnyLocalAddress && boundAddress is Inet4Address -> ipv4Loopback()
            boundAddress.isAnyLocalAddress -> ipv6Loopback()
            else -> return null
        }
    return BotEndpoint(targetAddress, port)
}

private fun BotLaunchResult.toGameResult(): BotClientRequestResult =
    when (this) {
        is BotLaunchResult.Accepted -> BotClientRequestResult.Accepted(count, reservedClients)
        is BotLaunchResult.InvalidCount -> BotClientRequestResult.InvalidCount(maximum)
        BotLaunchResult.CapacityReached -> BotClientRequestResult.CapacityReached
        BotLaunchResult.Busy -> BotClientRequestResult.Busy
        BotLaunchResult.Unavailable -> BotClientRequestResult.Unavailable
    }

private fun ipv4Loopback(): InetAddress =
    InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

private fun ipv6Loopback(): InetAddress =
    InetAddress.getByAddress(ByteArray(16).also { it[it.lastIndex] = 1 })
