package emu.server.js5

import emu.cache.store.FlatFileStore
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.js5.handler.Js5RequestHandler
import emu.protocol.osrs239.js5.buildJs5CodecRepository
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.writeFully
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Js5ServerTimeoutTest {
    @Test
    fun `frame idle timeout releases the bounded session permit`() = runBlocking {
        val root = Files.createTempDirectory("js5-idle").toFile()
        File(root, "cache/255/255.dat").also { it.parentFile.mkdirs() }
            .writeBytes(byteArrayOf(0, 0, 0, 0, 1, 7))
        val service =
            Js5Server(
                buildJs5CodecRepository(),
                Js5RequestHandler(FlatFileStore(root)),
                Js5ExecutionConfig(
                    workerThreads = 1,
                    maxConcurrentSessions = 1,
                    handshakeTimeout = 1.seconds,
                    frameIdleTimeout = 50.milliseconds,
                ),
            )
        val firstRead = ByteChannel(autoFlush = true)
        val firstWrite = ByteChannel(autoFlush = true)
        val first = launch { service.serve(firstRead, firstWrite) }
        firstRead.writeFully(handshakePayload())
        assertEquals(0, firstWrite.readByte().toInt() and 0xFF)
        withTimeout(1_000) { first.join() }

        val secondRead = ByteChannel(autoFlush = true)
        val secondWrite = ByteChannel(autoFlush = true)
        val second = launch { service.serve(secondRead, secondWrite) }
        secondRead.writeFully(handshakePayload())
        assertEquals(0, secondWrite.readByte().toInt() and 0xFF)
        secondRead.writeFully(byteArrayOf(1, 255.toByte(), 0, 255.toByte()))
        assertEquals(255, secondWrite.readByte().toInt() and 0xFF)

        second.cancelAndJoin()
        service.close()
    }

    private fun handshakePayload(): ByteArray = ByteArray(20).also { it[3] = 239.toByte() }
}
