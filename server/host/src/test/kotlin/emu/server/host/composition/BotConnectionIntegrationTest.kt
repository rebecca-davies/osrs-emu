package emu.server.host.composition

import emu.crypto.IsaacCipher
import emu.crypto.Rsa
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.login.codec.LoginBlockParser
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.bot.config.BotConfig
import emu.server.bot.connection.BotConnectionRunner
import emu.server.gateway.GatewayConfig
import emu.server.gateway.GatewayListener
import emu.server.gateway.GatewayRoute
import emu.server.gateway.GatewayRoutes
import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BotConnectionIntegrationTest {
    @Test
    fun `generated client crosses the gateway into an ISAAC game connection`() = runBlocking {
        val keyPair = Rsa.generateKeyPair(1_024)
        val observation = CompletableDeferred<Result<Unit>>()
        val routes =
            GatewayRoutes(
                js5Opcode = Js5Prot.HANDSHAKE.opcode,
                loginOpcode = LoginProt.INIT.opcode,
                js5 = GatewayRoute { _, _ -> error("unexpected JS5 route") },
                login = GatewayRoute { read, write ->
                    observeLoginAndGame(read, write, keyPair.modulus, keyPair.privateExp, observation)
                },
            )
        val listener = GatewayListener.bind(GatewayConfig(bindHost = "127.0.0.2", port = 0), routes)
        val selector = SelectorManager(Dispatchers.IO)
        val listenerJob = launch(Dispatchers.IO) { listener.run() }
        try {
            val endpoint = checkNotNull(listener.localEndpoint.botEndpointOrNull())
            assertEquals("127.0.0.2", endpoint.address.hostAddress)
            val runner =
                BotConnectionRunner(
                    BotConfig(loginTimeout = 2.seconds, keepAliveInterval = 10.milliseconds),
                    keyPair.publicKey,
                )
            withTimeout(5.seconds) {
                val runnerResult = runCatching { runner.run(endpoint, selector, Semaphore(1)) }
                observation.await().getOrThrow()
                runnerResult.getOrThrow()
            }
        } finally {
            selector.close()
            listenerJob.cancelAndJoin()
            listener.close()
        }
    }

    @Test
    fun `wildcard gateway endpoints select a loopback address of the same family`() {
        val ipv4 = checkNotNull(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 43_594).botEndpointOrNull())
        val ipv6 = checkNotNull(InetSocketAddress(InetAddress.getByName("::"), 43_594).botEndpointOrNull())

        assertIs<Inet4Address>(ipv4.address)
        assertIs<Inet6Address>(ipv6.address)
        assertEquals(43_594, ipv4.port)
        assertEquals(43_594, ipv6.port)
    }

    @Test
    fun `non-loopback gateway endpoint cannot enable bot clients`() {
        val address = InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, 1))

        assertNull(InetSocketAddress(address, 43_594).botEndpointOrNull())
    }

    private suspend fun observeLoginAndGame(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        modulus: BigInteger,
        privateExponent: BigInteger,
        observation: CompletableDeferred<Result<Unit>>,
    ) {
        try {
            val serverKey = 0x0102030405060708L
            val init = ByteBuffer.allocate(9).put(0).putLong(serverKey).array()
            write.writeFully(init)
            write.flush()

            check(read.readByte().toInt() and 0xFF == LoginProt.NEW_LOGIN.opcode)
            val lengthHigh = read.readByte().toInt() and 0xFF
            val lengthLow = read.readByte().toInt() and 0xFF
            val payloadLength = (lengthHigh shl 8) or lengthLow
            val payload = ByteArray(payloadLength)
            read.readFully(payload)
            checkRev239Header(payload)
            val parsed =
                checkNotNull((LoginBlockParser.parse(payload, modulus, privateExponent) as? LoginBlockParser.Result.Ok)?.parsed)
            try {
                check(parsed.serverKey == serverKey)
                write.writeByte(LoginResponse.SUCCESS.toByte())
                write.writeFully(ByteArray(LOGIN_SUCCESS_TRAILER_SIZE))
                write.flush()

                val encodedOpcode = read.readByte().toInt() and 0xFF
                val opcode = (encodedOpcode - IsaacCipher(parsed.seeds).nextInt()) and 0xFF
                check(opcode == GameClientProt.NO_TIMEOUT.opcode)
            } finally {
                parsed.clearPassword()
                payload.fill(0)
            }
            observation.complete(Result.success(Unit))
        } catch (failure: Throwable) {
            observation.complete(Result.failure(failure))
            throw failure
        }
    }

    private fun checkRev239Header(payload: ByteArray) {
        val header = ByteBuffer.wrap(payload)
        check(header.int == LoginProt.REVISION)
        check(header.int == LoginProt.SUBVERSION)
        check(header.int == LoginProt.BUILD_FLAGS)
        check(header.get().toInt() == 0)
        check(header.get().toInt() == 0)
        check(header.get().toInt() == 0)
    }

    private companion object {
        const val LOGIN_SUCCESS_TRAILER_SIZE = 35
    }
}
