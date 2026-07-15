package emu.server.js5

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Cache-update protocol operation owned by the JS5 service. */
interface Js5Server : AutoCloseable {
    suspend fun serve(read: ByteReadChannel, write: ByteWriteChannel)
}
