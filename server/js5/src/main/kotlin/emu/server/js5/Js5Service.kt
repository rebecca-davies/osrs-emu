package emu.server.js5

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Cache-update protocol operations exposed to the host coordinator. */
interface Js5Service : AutoCloseable {
    suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel)
}
