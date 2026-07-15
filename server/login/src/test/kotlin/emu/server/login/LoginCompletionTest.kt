package emu.server.login

import emu.crypto.RsaKeyPair
import emu.server.login.auth.LoginAuthenticator
import emu.server.login.config.LoginExecutionConfig
import emu.server.session.AuthenticatedSession
import emu.server.session.AuthenticationCompletion
import emu.server.session.AuthenticationDecision
import emu.server.session.AuthenticationRejection
import emu.server.session.IsaacBootstrap
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginCompletionTest {
    @Test
    fun `unavailable world writes the login server offline response`() = runBlocking {
        val write = ByteChannel(autoFlush = true)
        val login = AuthenticatedSession(TEST_ACCOUNT, IsaacBootstrap(1, 2, 3, 4))
        LoginServer(
            TEST_RSA_KEY,
            LoginAuthenticator { _, _ -> AuthenticationDecision.Rejected },
            LoginExecutionConfig(workerThreads = 1),
        ).use { server ->
            server.complete(
                write,
                login,
                AuthenticationCompletion.Rejected(AuthenticationRejection.WORLD_UNAVAILABLE),
            )
        }

        assertEquals(8, write.readByte().toInt() and 0xFF)
    }

    private companion object {
        val TEST_RSA_KEY =
            RsaKeyPair(
                modulus = BigInteger.valueOf(3_233),
                publicExp = BigInteger.valueOf(17),
                privateExp = BigInteger.valueOf(2_753),
            )
    }
}
