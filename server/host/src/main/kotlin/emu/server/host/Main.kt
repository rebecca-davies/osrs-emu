package emu.server.host

import emu.server.host.config.loadServerConfig
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    runServer(loadServerConfig())
}
