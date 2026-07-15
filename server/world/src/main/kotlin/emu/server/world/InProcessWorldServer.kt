package emu.server.world

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.pathfinding.CollisionMap
import emu.transport.codec.CodecRepository
import emu.persistence.character.CharacterSaveSink
import emu.persistence.chat.ChatAuditSink
import emu.server.world.admission.GameAdmission
import emu.server.world.config.GameConnectionConfig
import emu.server.world.session.runGameStage
import emu.server.world.runtime.WorldLifecycle
import emu.server.world.runtime.WorldRuntime
import emu.server.session.AuthenticatedPrincipal
import emu.server.session.ConnectionHandoff
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/** In-process world service for prepared character sessions. */
class InProcessWorldServer(
    private val codecs: CodecRepository,
    private val characterSaves: CharacterSaveSink,
    private val chatAudit: ChatAuditSink,
    private val connectionConfig: GameConnectionConfig,
    private val world: WorldRuntime,
    private val collision: CollisionMap,
    private val huffman: HuffmanCodec,
    private val admissions: GameAdmission,
    private val worldLifecycle: WorldLifecycle,
    private val dispatchers: WorldServerDispatchers,
) : WorldServer {
    private val started = AtomicBoolean(false)

    override fun start() {
        check(started.compareAndSet(false, true)) { "world server can only be started once" }
        worldLifecycle.start()
    }

    override suspend fun awaitTermination() {
        worldLifecycle.awaitTermination()
    }

    override suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision {
        if (!worldLifecycle.isRunning) return ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        return admissions.reserve(principal)
    }

    override suspend fun release(token: GameSessionToken) {
        admissions.release(token)
    }

    override suspend fun play(read: ByteReadChannel, write: ByteWriteChannel, handoff: ConnectionHandoff) {
        check(worldLifecycle.isRunning) { "world server is not running" }
        val player = admissions.take(handoff.reservation.token)
        if (player == null) {
            world.release(handoff.reservation.token)
            logger.warn { "world handoff has no live reservation for account ${handoff.connection.principal.accountId}" }
            return
        }
        try {
            withContext(dispatchers.io) {
                val accountId = handoff.connection.principal.accountId
                if (player.id != accountId) {
                    logger.warn { "world reservation account mismatch for account $accountId" }
                    return@withContext
                }
                val seeds = handoff.connection.isaac.toIntArray()
                runGameStage(
                    read,
                    write,
                    IsaacCipher(seeds),
                    IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 }),
                    codecs,
                    player,
                    world,
                    characterSaves,
                    connectionConfig = connectionConfig,
                    collisionMap = collision,
                    huffman = huffman,
                    chatAudit = chatAudit,
                    reservationToken = handoff.reservation.token,
                    ioDispatcher = dispatchers.io,
                )
            }
        } finally {
            world.release(handoff.reservation.token)
            admissions.finishSession()
        }
    }

    override suspend fun stop() {
        worldLifecycle.stop()
    }
}
