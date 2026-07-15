package emu.server.login

import emu.crypto.NopStreamCipher
import emu.crypto.RsaKeyPair
import emu.server.login.config.LoginExecutionConfig
import emu.server.login.wire.loginSuccessTrailer
import emu.server.login.wire.performLoginBlock
import emu.server.login.wire.performLoginInit
import emu.protocol.osrs239.login.codec.LoginResponseEncoder
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationRejection
import emu.server.session.AuthenticatedSession
import io.github.oshai.kotlinlogging.KotlinLogging
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

private val logger = KotlinLogging.logger {}

/** Bounded executor for login init, RSA parsing, authentication, and ISAAC bootstrap. */
class BoundedLoginServer(
    private val rsaKeyPair: RsaKeyPair?,
    private val authenticator: LoginAuthenticator,
    private val config: LoginExecutionConfig = LoginExecutionConfig(),
    private val dispatcher: ExecutorCoroutineDispatcher = loginDispatcher(config.workerThreads),
) : LoginServer {
    private val attempts = Semaphore(config.maxConcurrentAttempts)

    override suspend fun authenticate(read: ByteReadChannel, write: ByteWriteChannel): AuthenticatedSession? {
        if (!attempts.tryAcquire()) {
            logger.warn { "login attempt limit reached; rejecting connection" }
            return null
        }
        try {
            return try {
                withTimeout(config.authenticationTimeout) {
                    withContext(dispatcher) {
                        val serverKey = performLoginInit(write)
                        when (val opcode = read.readByte().toInt() and 0xFF) {
                            LoginProt.NEW_LOGIN.opcode, LoginProt.RECONNECT.opcode -> {
                                val keys = rsaKeyPair
                                if (keys == null) {
                                    logger.warn { "rejecting login block: no server RSA keypair loaded" }
                                    null
                                } else {
                                    performLoginBlock(
                                        read,
                                        write,
                                        serverKey,
                                        keys,
                                        reconnect = opcode == LoginProt.RECONNECT.opcode,
                                        authenticate = authenticator::authenticate,
                                    )
                                }
                            }

                            else -> {
                                logger.warn { "unexpected opcode $opcode after login init; closing connection" }
                                null
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                logger.debug { "login authentication timed out" }
                null
            } catch (_: EOFException) {
                logger.debug { "login client closed before authentication completed" }
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
                    }
            }
        write.writeFully(LoginResponseEncoder.encode(NopStreamCipher, LoginResponse(response)))
        if (completion is AuthenticationCompletion.Accepted && !login.reconnect) {
            write.writeFully(loginSuccessTrailer(login.connection.principal.privilege, completion.playerIndex))
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
