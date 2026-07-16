package emu.server.login

import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.login.auth.LoginAuthenticator
import emu.server.login.config.LoginExecutionConfig
import emu.server.login.wire.loginSuccessTrailer
import emu.server.login.wire.performLoginBlock
import emu.server.login.wire.performLoginInit
import emu.server.session.authentication.AuthenticatedSession
import emu.server.session.authentication.AuthenticationCompletion
import emu.server.session.authentication.AuthenticationRejection
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeFully
import java.io.EOFException
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns login handshakes, attempt limits, authentication, and worker execution. */
class LoginServer(
    private val rsaKeyPair: RsaKeyPair,
    private val authenticator: LoginAuthenticator,
    private val config: LoginExecutionConfig = LoginExecutionConfig(),
    private val dispatcher: ExecutorCoroutineDispatcher = loginDispatcher(config.workerThreads),
) : LoginService {
    private val attempts = Semaphore(config.maxConcurrentAttempts)

    override suspend fun authenticate(read: ByteReadChannel, write: ByteWriteChannel): AuthenticatedSession? {
        if (!attempts.tryAcquire()) return null
        try {
            return try {
                withTimeout(config.authenticationTimeout) {
                    withContext(dispatcher) {
                        val serverKey = performLoginInit(write)
                        when (read.readByte().toInt() and 0xFF) {
                            LoginProt.NEW_LOGIN.opcode -> {
                                performLoginBlock(
                                    read,
                                    write,
                                    serverKey,
                                    rsaKeyPair,
                                    authenticate = authenticator::authenticate,
                                )
                            }

                            else -> null
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (_: EOFException) {
                null
            } catch (failure: CancellationException) {
                throw failure
            }
        } finally {
            attempts.release()
        }
    }

    override suspend fun complete(
        write: ByteWriteChannel,
        login: AuthenticatedSession,
        completion: AuthenticationCompletion,
    ): Boolean {
        val response =
            when (completion) {
                is AuthenticationCompletion.Accepted -> LoginResponse.SUCCESS
                is AuthenticationCompletion.Rejected ->
                    when (completion.reason) {
                        AuthenticationRejection.ALREADY_ONLINE -> LoginResponse.ACCOUNT_ONLINE
                        AuthenticationRejection.WORLD_FULL -> LoginResponse.WORLD_FULL
                        AuthenticationRejection.WORLD_UNAVAILABLE -> LoginResponse.LOGIN_SERVER_OFFLINE
                    }
            }
        write.writeFully(LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(response)))
        if (completion is AuthenticationCompletion.Accepted) {
            write.writeFully(loginSuccessTrailer(login.account.privilege, completion.playerIndex))
        }
        write.flush()
        return completion is AuthenticationCompletion.Accepted
    }

    override fun close() = dispatcher.close()
}

private fun loginDispatcher(workerThreads: Int): ExecutorCoroutineDispatcher =
    Executors.newFixedThreadPool(workerThreads) { task ->
        Thread(task, "login-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()
