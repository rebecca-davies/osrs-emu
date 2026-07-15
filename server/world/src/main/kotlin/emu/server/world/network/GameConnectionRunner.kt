package emu.server.world.network

import emu.crypto.IsaacCipher
import emu.game.action.GameInputQueue
import emu.persistence.character.PlayerRecord
import emu.server.session.GameSessionToken
import emu.server.world.config.GameConnectionConfig
import emu.server.world.runtime.WorldCommandQueue
import emu.transport.codec.CodecRepository
import emu.transport.pipeline.OutboundSession
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** Owns socket/cipher coroutines while the authoritative player remains in the shared world. */
class GameConnectionRunner(
    private val codecs: CodecRepository,
    private val config: GameConnectionConfig,
    private val worldCommands: WorldCommandQueue,
    private val inbound: GameInboundReader,
    private val ioDispatcher: CoroutineDispatcher,
) {
    internal suspend fun run(
        read: ByteReadChannel,
        write: ByteWriteChannel,
        inboundCipher: IsaacCipher,
        outboundCipher: IsaacCipher,
        player: PlayerRecord,
        token: GameSessionToken,
        beginSession: suspend (Int) -> Boolean,
    ): Boolean =
        try {
            withContext(ioDispatcher) {
                val actions = GameInputQueue(config.inputQueue)
                val output = GameOutputQueue(config.outputQueueCapacity)
                val writer = GameOutboundWriter(OutboundSession(codecs, outboundCipher, write))
                coroutineScope {
                    var writerJob: Job? = null
                    try {
                        val attachment = worldCommands.attach(token, player, actions, output)
                        writerJob = launch { output.run(writer::write) }
                        val login = attachment.login.await() ?: return@coroutineScope false
                        if (!beginSession(login.playerIndex)) return@coroutineScope false
                        withTimeout(INITIAL_OUTPUT_TIMEOUT) {
                            output.submitAndAwait(login.initialOutput)
                        }
                        worldCommands.activate(token)
                        val readerJob =
                            launch {
                                try {
                                    inbound.run(read, inboundCipher, actions)
                                } finally {
                                    withContext(NonCancellable) { worldCommands.disconnect(token) }
                                }
                            }
                        try {
                            attachment.removed.await()
                        } finally {
                            readerJob.cancelAndJoin()
                        }
                        true
                    } finally {
                        output.close()
                        val activeWriter = writerJob
                        if (activeWriter != null) {
                            withContext(NonCancellable) {
                                try {
                                    withTimeout(OUTPUT_DRAIN_TIMEOUT) { activeWriter.join() }
                                } catch (_: TimeoutCancellationException) {
                                    activeWriter.cancelAndJoin()
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                worldCommands.release(token)
                worldCommands.disconnect(token)
            }
        }

    private companion object {
        val INITIAL_OUTPUT_TIMEOUT = 10.seconds
        val OUTPUT_DRAIN_TIMEOUT = 2.seconds
    }
}
