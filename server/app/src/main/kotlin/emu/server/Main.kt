package emu.server

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    runServer(loadServerConfig())
}
