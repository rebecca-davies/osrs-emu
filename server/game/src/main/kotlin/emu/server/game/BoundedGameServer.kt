package emu.server.game

import emu.cache.map.CacheMapRepository
import emu.cache.map.CacheObjectDefinitionRepository
import emu.cache.store.Store
import emu.crypto.IsaacCipher
import emu.server.game.admission.GameAdmission
import emu.server.game.config.GameExecutionConfig
import emu.server.game.network.loadHuffmanCodec
import emu.server.game.map.CacheCollisionMap
import emu.server.game.session.runGameStage
import emu.server.game.world.WorldRuntime
import emu.netcore.codec.CodecRepository
import emu.persistence.ChatAuditSink
import emu.persistence.PlayerRepository
import emu.server.session.ConnectionHandoff
import emu.server.session.GameSessionToken
import emu.server.session.AuthenticatedPrincipal
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/** Bounded game IO and single-thread world runtime for prepared character sessions. */
class BoundedGameServer(
    store: Store,
    private val codecs: CodecRepository,
    private val players: PlayerRepository,
    private val chatAudit: ChatAuditSink,
    private val config: GameExecutionConfig = GameExecutionConfig(),
    private val worldDispatcher: ExecutorCoroutineDispatcher = newWorldDispatcher(),
    private val ioDispatcher: ExecutorCoroutineDispatcher = newGameIoDispatcher(config.ioWorkerThreads),
) : GameServer {
    private val world = WorldRuntime(maxPlayerIndex = config.maxConcurrentSessions)
    private val collision = CacheCollisionMap(CacheMapRepository(store), CacheObjectDefinitionRepository(store))
    private val huffman = loadHuffmanCodec(store)
    private val admissions =
        GameAdmission(world, config.maxConcurrentSessions) { accountId ->
            withContext(ioDispatcher) { players.loadCharacter(accountId) }
        }
    private val started = AtomicBoolean(false)
    private val worldScope = CoroutineScope(SupervisorJob() + worldDispatcher)
    private var worldJob: kotlinx.coroutines.Job? = null

    override fun start() {
        check(started.compareAndSet(false, true)) { "game server can only be started once" }
        worldJob = worldScope.launch { world.run() }
    }

    override suspend fun reserve(principal: AuthenticatedPrincipal): ReservationDecision {
        if (!started.get()) return ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE)
        return admissions.reserve(principal)
    }

    override suspend fun release(token: GameSessionToken) {
        admissions.release(token)
    }

    override suspend fun play(read: ByteReadChannel, write: ByteWriteChannel, handoff: ConnectionHandoff) {
        check(started.get()) { "game server has not started" }
        val player = admissions.take(handoff.reservation.token)
        if (player == null) {
            world.release(handoff.reservation.token)
            logger.warn { "game handoff has no live reservation for account ${handoff.connection.principal.accountId}" }
            return
        }
        try {
            withContext(ioDispatcher) {
                val accountId = handoff.connection.principal.accountId
                if (player.id != accountId) {
                    logger.warn { "game reservation account mismatch for account $accountId" }
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
                    players::saveSession,
                    idleTimeout = config.idleTimeout,
                    collisionMap = collision,
                    huffman = huffman,
                    chatAudit = chatAudit,
                    reservationToken = handoff.reservation.token,
                    ioDispatcher = ioDispatcher,
                )
            }
        } finally {
            world.release(handoff.reservation.token)
            admissions.finishSession()
        }
    }

    override suspend fun stop() {
        worldJob?.cancelAndJoin()
        worldJob = null
    }

    override fun close() {
        ioDispatcher.close()
        worldDispatcher.close()
    }
}

private fun newWorldDispatcher(): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { task ->
        Thread(task, "game-world").apply { isDaemon = true }
    }.asCoroutineDispatcher()

private fun newGameIoDispatcher(workerThreads: Int): ExecutorCoroutineDispatcher =
    Executors.newFixedThreadPool(workerThreads) { task ->
        Thread(task, "game-io").apply { isDaemon = true }
    }.asCoroutineDispatcher()
