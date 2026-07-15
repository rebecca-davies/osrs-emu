package emu.server.login

import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticatedSession
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

/** Login handshake and completion operations exposed to the host coordinator. */
interface LoginService : AutoCloseable {
    suspend fun authenticate(read: ByteReadChannel, write: ByteWriteChannel): AuthenticatedSession?

    suspend fun complete(
        write: ByteWriteChannel,
        login: AuthenticatedSession,
        completion: AuthenticationCompletion,
    ): Boolean
}
