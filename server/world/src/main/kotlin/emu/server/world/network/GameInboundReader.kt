package emu.server.world.network

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.action.PlayerActionSink
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.transport.codec.CodecRepository
import emu.transport.pipeline.HandlerRepositoryBuilder
import emu.transport.pipeline.ProtocolStage
import emu.transport.prot.Prot
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** Frames socket bytes and queues decoded actions without touching world-owned player state. */
class GameInboundReader(
    private val codecs: CodecRepository,
    private val huffman: HuffmanCodec,
    private val idleTimeout: Duration,
) {
    internal suspend fun run(
        read: ByteReadChannel,
        cipher: IsaacCipher,
        actions: PlayerActionSink,
    ) {
        val handlers = HandlerRepositoryBuilder().installGameHandlers(actions, huffman).build()
        val stage =
            ProtocolStage(
                codecs,
                handlers,
                readOpcode = { channel ->
                    withTimeout(idleTimeout) {
                        ((channel.readByte().toInt() and 0xFF) - cipher.nextInt()) and 0xFF
                    }
                },
                readPayload = { channel, prot ->
                    withTimeout(idleTimeout) { readPayload(channel, prot) }
                },
                findProt = GameClientProt::find,
            )
        try {
            stage.run(read) { message ->
                error("inbound game handler attempted to emit ${message.javaClass.simpleName}")
            }
        } catch (_: TimeoutCancellationException) {
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Protocol errors and remote disconnects terminate only this player connection.
        }
    }

    private suspend fun readPayload(read: ByteReadChannel, prot: Prot): ByteArray {
        val size =
            when (prot.size) {
                Prot.VAR_BYTE -> read.readByte().toInt() and 0xFF
                Prot.VAR_SHORT -> {
                    val high = read.readByte().toInt() and 0xFF
                    val low = read.readByte().toInt() and 0xFF
                    (high shl 8) or low
                }
                else -> prot.size
            }
        require(size in 0..MAX_GAME_PACKET_SIZE) {
            "invalid game packet size $size for opcode ${prot.opcode}"
        }
        return ByteArray(size).also { read.readFully(it) }
    }

    private companion object {
        const val MAX_GAME_PACKET_SIZE = 10_000
    }
}
