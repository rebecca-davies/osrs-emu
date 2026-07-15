package emu.server.login

import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationDecision
import emu.server.session.AuthenticatedSession
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

fun interface LoginAuthenticator {
    fun authenticate(username: String, password: CharArray): AuthenticationDecision
}

/** Authentication and completion operations owned by the login service. */
interface LoginServer : AutoCloseable {
    suspend fun authenticate(read: ByteReadChannel, write: ByteWriteChannel): AuthenticatedSession?

    suspend fun complete(
        write: ByteWriteChannel,
        login: AuthenticatedSession,
        completion: AuthenticationCompletion,
    ): Boolean
}
