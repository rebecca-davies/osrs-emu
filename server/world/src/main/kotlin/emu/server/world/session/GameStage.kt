package emu.server.world.session

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.chat.PlayerChatQueue
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.PlayerButtonQueue
import emu.server.world.network.GameOutboundMailbox
import emu.server.world.network.GameOutboundWriter
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSink
import emu.server.world.player.PlayerChatState
import emu.server.world.player.PlayerSessionControl
import emu.server.world.player.PlayerVarpTypes
import emu.server.world.network.installGameHandlers
import emu.server.world.player.playerButtonActions
import emu.server.world.player.playerChatActions
import emu.server.world.runtime.WorldSessionRegistry
import emu.server.session.GameSessionToken
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.OutboundSession
import emu.netcore.pipeline.ProtocolStage
import emu.netcore.prot.Prot
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.prot.GameClientProt
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/** Inbound silence allowed after login; outbound world cycles continue during this interval. */
val GAME_IDLE_TIMEOUT: Duration = 30.seconds

/** Lumbridge, the default tile for a newly created account. */
internal const val SPAWN_PLANE = 0
internal const val SPAWN_X = 3222
internal const val SPAWN_Y = 3218

/**
 * Attaches an authenticated character to the shared world, sends its initial cycle, drains inbound
 * packets, and persists session state on exit. The bounded outbound mailbox isolates the world
 * clock from socket backpressure while one writer retains exclusive cipher and channel ownership.
 * [maxTicks] is a deterministic test limit.
 */
internal suspend fun runGameStage(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    inboundCipher: IsaacCipher,
    outboundCipher: IsaacCipher,
    gameCodecs: CodecRepository,
    player: PlayerRecord,
    worldSessions: WorldSessionRegistry,
    saveSession: (PlayerSessionSave) -> Unit,
    idleTimeout: Duration = GAME_IDLE_TIMEOUT,
    maxTicks: Int = Int.MAX_VALUE,
    collisionMap: CollisionMap = OpenCollisionMap,
    huffman: HuffmanCodec = HuffmanCodec(ByteArray(256) { 8 }),
    chatAudit: ChatAuditSink = ChatAuditSink { true },
    reservationToken: GameSessionToken? = null,
    ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
): Unit {
    val sessionStarted = System.nanoTime()
    val outboundMailbox = GameOutboundMailbox(OUTBOUND_BATCH_CAPACITY)
    val outboundWriter = GameOutboundWriter(OutboundSession(gameCodecs, outboundCipher, write))
    val playerVarps = initialPlayerVarps(player.varps)
    val movement =
        initialPlayerMovement(
            player.position,
            playerVarps[PlayerVarpTypes.RUN_MODE] == 1,
            collisionMap,
        )
    val routeRequests = PlayerRouteRequestQueue()
    val buttonClicks = PlayerButtonQueue()
    val chatInputs = PlayerChatQueue()
    val chatState = PlayerChatState()
    val sessionControl = PlayerSessionControl()
    val buttonActions = playerButtonActions(movement, playerVarps, sessionControl)
    val chatActions = playerChatActions(player.id, player.rank, playerVarps, chatState, chatAudit, huffman)
    val gameLoop = GameLoop(
        playerId = player.id,
        output = outboundMailbox,
        playerMovement = movement,
        routeRequests = routeRequests,
        buttonClicks = buttonClicks,
        buttonActions = buttonActions,
        playerVarps = playerVarps,
        chatInputs = chatInputs,
        chatActions = chatActions,
        chatState = chatState,
        sessionControl = sessionControl,
        onProfileReport = { snapshot ->
            adminCycleReport(player.rank, snapshot)?.let {
                if (!outboundMailbox.offer(GameOutputBatch.packet(it))) {
                    logger.warn { "game stage: dropped admin profile report for saturated player ${player.id}" }
                }
            }
        },
        maxCycles = maxTicks,
    )
    try {
        coroutineScope {
            val admitted =
                reservationToken?.let { worldSessions.attach(it, gameLoop, startActive = false) }
                    ?: worldSessions.register(gameLoop, startActive = false)
            val localPlayerIndex = admitted.playerIndex.await()
            if (localPlayerIndex == null) {
                logger.warn { "game stage: rejected duplicate, overloaded, or full session for player ${player.id}" }
                return@coroutineScope
            }
            try {
                val writerJob = launch(ioDispatcher) { outboundMailbox.run(outboundWriter::write) }
                try {
                    val initialBatch = initialGameCycle(
                        spawnPlane = player.position.plane,
                        spawnX = player.position.x,
                        spawnY = player.position.y,
                        localPlayerIndex = localPlayerIndex,
                        appearance = playerAppearance(player.displayName),
                        accountVarps = initialAccountVarps(playerVarps),
                        chatFilters = initialChatFilters(playerVarps),
                    )
                    withTimeout(INITIAL_BATCH_TIMEOUT) {
                        outboundMailbox.submitAndAwait(initialBatch)
                    }
                    logger.info {
                        "game stage: sent capture-shaped initial cycle " +
                            "(world group + full neutral frame/state)"
                    }
                    playerVarps.markClientSynchronized()
                    worldSessions.activate(player.id)

                    val readJob = launch {
                        try {
                            drainGameInbound(
                                read,
                                outboundMailbox,
                                inboundCipher,
                                gameCodecs,
                                routeRequests,
                                buttonClicks,
                                idleTimeout,
                                chatInputs,
                                huffman,
                            )
                        } finally {
                            if (!admitted.removed.isCompleted) {
                                withContext(NonCancellable) { worldSessions.remove(player.id) }
                            }
                        }
                    }

                    admitted.removed.await()
                    readJob.cancelAndJoin()
                } finally {
                    outboundMailbox.close()
                    withContext(NonCancellable) {
                        try {
                            withTimeout(OUTBOUND_DRAIN_TIMEOUT) { writerJob.join() }
                        } catch (_: TimeoutCancellationException) {
                            logger.warn { "game stage: timed out draining outbound batches for player ${player.id}" }
                            writerJob.cancelAndJoin()
                        }
                    }
                }
            } finally {
                if (!admitted.removed.isCompleted) {
                    withContext(NonCancellable) { worldSessions.remove(player.id) }
                }
            }
        }
    } finally {
        val elapsedSeconds = ((System.nanoTime() - sessionStarted) / NANOS_PER_SECOND).coerceAtLeast(0)
        val finalPosition = PlayerPosition(movement.position.x, movement.position.y, movement.position.plane)
        try {
            withContext(NonCancellable + ioDispatcher) {
                withTimeout(SESSION_SAVE_TIMEOUT) {
                    saveSession(
                        PlayerSessionSave(
                            playerId = player.id,
                            position = finalPosition,
                            playedSeconds = elapsedSeconds,
                            dirtyVarps = playerVarps.dirtyPersistentValues(),
                        ),
                    )
                }
            }
            logger.info { "game stage: saved player ${player.id} session state" }
        } catch (failure: Throwable) {
            logger.error(failure) { "game stage: failed to save player ${player.id} session state" }
        }
    }
}

/** Creates the live movement session with server-authoritative unlimited run enabled. */
internal fun initialPlayerMovement(
    position: PlayerPosition,
    runEnabled: Boolean = false,
    collisionMap: CollisionMap = OpenCollisionMap,
): PlayerMovement =
    PlayerMovement(Tile(position.x, position.y, position.plane), collisionMap).apply {
        this.runEnabled = runEnabled
    }

/**
 * Runs the revision-neutral [ProtocolStage] over rev-239's complete inbound size table. Implemented
 * packets are decoded and type-dispatched; declared but unsupported packets are framed and dropped,
 * preserving one ISAAC advance per opcode. Both opcode and whole-payload reads have [idleTimeout]
 * deadlines and variable bodies are capped to the injected client's 10,000-byte packet buffer.
 */
internal suspend fun drainGameInbound(
    read: ByteReadChannel,
    output: GameOutputSink,
    inboundCipher: IsaacCipher,
    gameCodecs: CodecRepository,
    routeRequests: PlayerRouteRequestQueue,
    buttonClicks: PlayerButtonQueue,
    idleTimeout: Duration,
    chatInputs: PlayerChatQueue = PlayerChatQueue(),
    huffman: HuffmanCodec = HuffmanCodec(ByteArray(256) { 8 }),
) {
    val handlers = HandlerRepositoryBuilder().installGameHandlers(routeRequests, buttonClicks, chatInputs, huffman).build()
    val stage =
        ProtocolStage(
            gameCodecs,
            handlers,
            readOpcode = { channel ->
                withTimeout(idleTimeout) {
                    ((channel.readByte().toInt() and 0xFF) - inboundCipher.nextInt()) and 0xFF
                }
            },
            readPayload = { channel, prot ->
                withTimeout(idleTimeout) { readGamePayload(channel, prot) }
            },
            findProt = GameClientProt::find,
        )
    try {
        stage.run(read) { message ->
            check(output.offer(GameOutputBatch.packet(message))) { "outbound mailbox saturated" }
        }
    } catch (e: TimeoutCancellationException) {
        logger.info { "game stage: idle for $idleTimeout during an inbound packet; closing connection" }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.info { "game stage: inbound read ended (${e.javaClass.simpleName}: ${e.message}); ending stage" }
    }
}

/** Reads one fixed, var-byte, or var-short body with a hard allocation bound. */
private suspend fun readGamePayload(read: ByteReadChannel, prot: Prot): ByteArray {
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
    require(size in 0..MAX_GAME_PACKET_SIZE) { "invalid game packet size $size for opcode ${prot.opcode}" }
    return ByteArray(size).also { read.readFully(it) }
}

private const val MAX_GAME_PACKET_SIZE = 10_000
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val OUTBOUND_BATCH_CAPACITY = 4
private val SESSION_SAVE_TIMEOUT = 5.seconds
private val INITIAL_BATCH_TIMEOUT = 10.seconds
private val OUTBOUND_DRAIN_TIMEOUT = 2.seconds
