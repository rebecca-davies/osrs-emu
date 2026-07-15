package emu.gateway.login

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.chat.PlayerChatQueue
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.PlayerButtonQueue
import emu.gateway.game.GameOutboundMailbox
import emu.gateway.game.GameOutboundWriter
import emu.gateway.game.GameOutputBatch
import emu.gateway.game.GameOutputSink
import emu.gateway.game.PlayerChatState
import emu.gateway.game.PlayerSessionControl
import emu.gateway.game.PlayerVarpTypes
import emu.gateway.game.installGameHandlers
import emu.gateway.game.playerButtonActions
import emu.gateway.game.playerChatActions
import emu.gateway.world.WorldRuntime
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.OutboundSession
import emu.netcore.pipeline.ProtocolStage
import emu.netcore.prot.Prot
import emu.persistence.ChatAuditSink
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRecord
import emu.protocol.osrs239.game.prot.GameClientProt
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
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

/** Inbound GAME-stage idle timeout, reset after each received packet. */
val GAME_IDLE_TIMEOUT: Duration = 30.seconds

/** Lumbridge, the default tile for a newly created account. */
internal const val SPAWN_PLANE = 0
internal const val SPAWN_X = 3222
internal const val SPAWN_Y = 3218

/**
 * Authenticated game stage: puts the client in-world and keeps its session lifecycle synchronized
 * with the shared world.
 *
 * Sends [initialGameCycle] before activation. [WorldRuntime] advances [GameLoop] on the shared
 * clock while [drainGameInbound] frames and dispatches client packets.
 *
 * When either side ends (the client disconnects, or it goes idle past [idleTimeout]) the other is
 * cancelled and the connection is handed back to [emu.gateway.handleConnection]'s `finally` to close.
 *
 * A bounded mailbox carries atomic output batches to the per-connection writer, the sole owner of
 * [OutboundSession], outbound ISAAC, and the socket.
 *
 * A fresh login sets [sendLoginInfo] so its rank and admitted player index are written before that
 * writer starts. Reconnects omit the trailer, matching the rev-239 login-state contract.
 *
 * [maxTicks] is a per-session test seam; production leaves it unbounded.
 */
internal suspend fun runGameStage(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    inboundCipher: IsaacCipher,
    outboundCipher: IsaacCipher,
    gameCodecs: CodecRepository,
    player: PlayerRecord,
    worldRuntime: WorldRuntime,
    saveSession: (Long, PlayerPosition, Long, Map<Int, Int>) -> Unit,
    idleTimeout: Duration = GAME_IDLE_TIMEOUT,
    maxTicks: Int = Int.MAX_VALUE,
    collisionMap: CollisionMap = OpenCollisionMap,
    huffman: HuffmanCodec = HuffmanCodec(ByteArray(256) { 8 }),
    chatAudit: ChatAuditSink = ChatAuditSink { true },
    sendLoginInfo: Boolean = false,
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
            val registration = worldRuntime.register(gameLoop, startActive = false)
            val localPlayerIndex = registration.playerIndex.await()
            if (localPlayerIndex == null) {
                logger.warn { "game stage: rejected duplicate, overloaded, or full session for player ${player.id}" }
                return@coroutineScope
            }
            try {
                if (sendLoginInfo) {
                    write.writeFully(loginSuccessTrailer(player.rank, localPlayerIndex))
                    write.flush()
                }
                val writerJob = launch(Dispatchers.IO) { outboundMailbox.run(outboundWriter::write) }
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
                    worldRuntime.activate(player.id)

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
                            if (!registration.removed.isCompleted) {
                                withContext(NonCancellable) { worldRuntime.remove(player.id) }
                            }
                        }
                    }

                    registration.removed.await()
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
                if (!registration.removed.isCompleted) {
                    withContext(NonCancellable) { worldRuntime.remove(player.id) }
                }
            }
        }
    } finally {
        val elapsedSeconds = ((System.nanoTime() - sessionStarted) / NANOS_PER_SECOND).coerceAtLeast(0)
        val finalPosition = PlayerPosition(movement.position.x, movement.position.y, movement.position.plane)
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                withTimeout(SESSION_SAVE_TIMEOUT) {
                    saveSession(player.id, finalPosition, elapsedSeconds, playerVarps.dirtyPersistentValues())
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
