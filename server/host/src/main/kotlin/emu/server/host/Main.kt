package emu.server.host

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    runServer(loadServerConfig())
}
