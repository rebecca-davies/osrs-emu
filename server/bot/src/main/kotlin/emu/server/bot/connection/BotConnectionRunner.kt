package emu.server.bot.connection

import emu.buffer.JagexBuffer
import emu.crypto.IsaacCipher
import emu.crypto.RsaPublicKey
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.prot.LoginProt
import emu.server.bot.account.BotAccountGenerator
import emu.server.bot.config.BotConfig
import emu.server.bot.wire.BotLoginBlock
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import java.security.SecureRandom
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout

/** Connects one generated account through the normal localhost gateway, login, and game stages. */
class BotConnectionRunner(
    private val config: BotConfig,
    private val publicKey: RsaPublicKey,
) : BotConnection {
    private val accounts = BotAccountGenerator()
    private val random = SecureRandom()

    override suspend fun run(
        endpoint: BotEndpoint,
        selector: SelectorManager,
        loginPermits: Semaphore,
    ) {
        loginPermits.acquire()
        var loginPermitHeld = true
        var socket: Socket? = null
        try {
            lateinit var read: ByteReadChannel
            lateinit var write: ByteWriteChannel
            val seeds = IntArray(ISAAC_SEED_COUNT) { random.nextInt() }
            withTimeout(config.loginTimeout) {
                socket =
                    aSocket(selector)
                        .tcp()
                        .connect(InetSocketAddress(endpoint.address.hostAddress, endpoint.port))
                read = checkNotNull(socket).openReadChannel()
                write = checkNotNull(socket).openWriteChannel(autoFlush = false)
                login(read, write, seeds)
            }
            loginPermits.release()
            loginPermitHeld = false
            runGame(read, write, seeds)
        } finally {
            if (loginPermitHeld) loginPermits.release()
            socket?.close()
        }
    }

    private suspend fun login(read: ByteReadChannel, write: ByteWriteChannel, seeds: IntArray) {
        write.writeByte(LoginProt.INIT.opcode.toByte())
        write.flush()
        val init = ByteArray(SESSION_KEY_RESPONSE_SIZE)
        read.readFully(init)
        require(init[0].toInt() == 0) { "login init was rejected" }
        val serverKey = JagexBuffer(init, pos = 1).readLong()
        val credentials = accounts.next()
        val payload =
            try {
                BotLoginBlock.encode(publicKey, seeds, serverKey, credentials.username, credentials.password)
            } finally {
                credentials.clear()
            }
        try {
            write.writeByte(LoginProt.NEW_LOGIN.opcode.toByte())
            write.writeByte((payload.size ushr 8).toByte())
            write.writeByte(payload.size.toByte())
            write.writeFully(payload)
            write.flush()
        } finally {
            payload.fill(0)
        }
        val response = read.readByte().toInt() and 0xFF
        require(response == LoginResponse.SUCCESS) { "bot login was rejected with response $response" }
        read.readFully(ByteArray(LOGIN_SUCCESS_TRAILER_SIZE))
    }

    private suspend fun runGame(read: ByteReadChannel, write: ByteWriteChannel, seeds: IntArray) = coroutineScope {
        val drain = launch { drainOutput(read) }
        val keepAlive = launch { keepAlive(write, IsaacCipher(seeds)) }
        try {
            drain.join()
        } finally {
            keepAlive.cancelAndJoin()
        }
    }

    private suspend fun drainOutput(read: ByteReadChannel) {
        val buffer = ByteArray(OUTPUT_BUFFER_SIZE)
        while (read.readAvailable(buffer) >= 0) {
            // Bot clients deliberately discard output after consuming it from the socket.
        }
    }

    private suspend fun keepAlive(write: ByteWriteChannel, cipher: IsaacCipher) {
        while (true) {
            delay(config.keepAliveInterval)
            write.writeByte(((GameClientProt.NO_TIMEOUT.opcode + cipher.nextInt()) and 0xFF).toByte())
            write.flush()
        }
    }

    private companion object {
        const val ISAAC_SEED_COUNT = 4
        const val SESSION_KEY_RESPONSE_SIZE = 9
        const val LOGIN_SUCCESS_TRAILER_SIZE = 35
        const val OUTPUT_BUFFER_SIZE = 8 * 1_024
    }
}
