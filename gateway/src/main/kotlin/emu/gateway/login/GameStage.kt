package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.PlayerButtonQueue
import emu.game.chat.PlayerChatQueue
import emu.compression.HuffmanCodec
import emu.persistence.ChatAuditSink
import emu.gateway.game.PlayerSessionControl
import emu.gateway.game.PlayerVarpTypes
import emu.gateway.game.playerButtonActions
import emu.gateway.game.installGameHandlers
import emu.gateway.game.PlayerChatState
import emu.gateway.game.playerChatActions
import emu.gateway.world.WorldRuntime
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.HandlerRepositoryBuilder
import emu.netcore.pipeline.OutboundSession
import emu.netcore.pipeline.ProtocolStage
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRecord
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

/**
 * Idle-read deadline for a connection that has reached the GAME stage (a logged-in client). Unlike
 * [emu.gateway.HANDSHAKE_TIMEOUT], this is deliberately generous: a real client can sit idle between
 * player-initiated packets, and this deadline resets on every packet the inbound drain receives, so
 * it only fires on a connection that stops sending anything at all for the whole window — the
 * CLAUDE.md §10 "read/idle timeouts at the edge" requirement, applied without penalizing a
 * normally-active client. Note this bounds only *inbound* silence; the server still emits its
 * per-tick PLAYER_INFO heartbeat the whole time (see [GameLoop]).
 */
val GAME_IDLE_TIMEOUT: Duration = 30.seconds

/** Lumbridge, the default tile for a newly created account. */
internal const val SPAWN_PLANE = 0
internal const val SPAWN_X = 3222
internal const val SPAWN_Y = 3218

/**
 * Authenticated game stage: puts the client in-world and keeps its session lifecycle synchronized
 * with the shared world.
 *
 * After inactive world admission it sends [sendInitialGameCycle]: the Lumbridge rebuild plus the
 * required atomic entity/player/NPC/zone group and neutral rev-239 frame/account state. It then
 * activates the player while a sibling coroutine drains this connection's inbound protocol stream:
 *  - the **world runtime** advances [GameLoop] from the same authoritative 600ms clock as every
 *    other player and sends the atomic player/NPC heartbeat that keeps the client in-game.
 *  - an **inbound protocol stage** ([drainGameInbound]) that frames all rev-239 client packets,
 *    dispatches implemented messages, and enforces the idle timeout.
 *
 * When either side ends (the client disconnects, or it goes idle past [idleTimeout]) the other is
 * cancelled and the connection is handed back to [emu.gateway.handleConnection]'s `finally` to close.
 *
 * Every server->client packet is written through the same
 * [OutboundSession] — a thin wrapper over [emu.netcore.pipeline.writePacket] and the shared
 * [gameCodecs] registry — so each advances the outbound ISAAC keystream exactly once for its opcode
 * and this function never hand-rolls opcode/body bytes (CLAUDE.md §1/§9).
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
): Unit {
    val sessionStarted = System.nanoTime()
    val session = OutboundSession(gameCodecs, outboundCipher, write)
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
        session = session,
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
            adminCycleReport(player.rank, snapshot)?.let { session.send(it) }
        },
        maxCycles = maxTicks,
    )
    try {
        coroutineScope {
            val registration = worldRuntime.register(gameLoop, startActive = false)
            if (!registration.admitted.await()) {
                logger.warn { "game stage: rejected duplicate or overloaded session for player ${player.id}" }
                return@coroutineScope
            }
            try {
                sendInitialGameCycle(
                    session = session,
                    spawnPlane = player.position.plane,
                    spawnX = player.position.x,
                    spawnY = player.position.y,
                    localPlayerIndex = LOCAL_PLAYER_INDEX,
                    appearance = playerAppearance(player.displayName),
                    accountVarps = initialAccountVarps(playerVarps),
                    chatFilters = initialChatFilters(playerVarps),
                )
                playerVarps.markClientSynchronized()
                worldRuntime.activate(player.id)

                val readJob = launch {
                    try {
                        drainGameInbound(
                            read,
                            write,
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
    write: ByteWriteChannel,
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
        stage.run(read, write)
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
private val SESSION_SAVE_TIMEOUT = 5.seconds
