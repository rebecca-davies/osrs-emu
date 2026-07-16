package emu.server.game.network.connection

import emu.crypto.IsaacCipher
import emu.game.action.IncomingPlayerActionQueue
import emu.persistence.character.model.CharacterRecord
import emu.server.game.config.GameConnectionConfig
import emu.server.game.network.input.GameInboundReader
import emu.server.game.network.output.GameOutboundWriter
import emu.server.game.network.output.GameOutputQueue
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.session.handoff.ConnectionHandoff
import emu.transport.codec.CodecRepository
import emu.transport.pipeline.outbound.PacketWriter
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        character: CharacterRecord,
        handoff: ConnectionHandoff,
        completeLogin: suspend (Int) -> Boolean,
    ): Boolean {
        val token = handoff.reservation.token
        val seeds = handoff.isaac.toIntArray()
        val inboundCipher = IsaacCipher(seeds)
        val outboundCipher =
            IsaacCipher(IntArray(seeds.size) { seeds[it] + OUTBOUND_ISAAC_OFFSET })
        return try {
            withContext(ioDispatcher) {
                val actions = IncomingPlayerActionQueue(config.incomingActions)
                val output = GameOutputQueue(config.outputQueueCapacity)
                val writer = GameOutboundWriter(PacketWriter(codecs, outboundCipher, write))
                coroutineScope {
                    var writerJob: Job? = null
                    try {
                        val attachment =
                            worldCommands.attach(
                                token,
                                character,
                                handoff.account.privilege,
                                actions,
                                output,
                            )
                        writerJob = launch { output.run(writer::write) }
                        val login = attachment.login.await() ?: return@coroutineScope false
                        if (!completeLogin(login.playerIndex)) return@coroutineScope false
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
    }

    private companion object {
        const val OUTBOUND_ISAAC_OFFSET = 50
        val INITIAL_OUTPUT_TIMEOUT = 10.seconds
        val OUTPUT_DRAIN_TIMEOUT = 2.seconds
    }
}
