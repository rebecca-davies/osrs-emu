package emu.server.game.session

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.GameCycle
import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonActionRegistry
import emu.game.ui.PlayerButtonQueue
import emu.game.chat.ChatActionRegistry
import emu.game.chat.PlayerChatQueue
import emu.game.varp.PlayerVarps
import emu.server.game.player.PlayerSessionControl
import emu.server.game.player.PlayerChatState
import emu.server.game.network.GameOutputBatch
import emu.server.game.network.GameOutputSink
import emu.server.game.world.WorldParticipant
import emu.server.game.world.WorldParticipantResult
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.Logout
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerMovement as ProtocolPlayerMovement
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * One player's protocol-facing work within the shared world tick.
 *
 * A freshly logged-in client only stays in-game while the server keeps feeding it a PLAYER_INFO
 * (GPI) packet **every** cycle. Scheduling belongs exclusively to
 * [emu.server.game.world.WorldRuntime]; this class cannot create an independent player clock.
 *
 * [GameCycle] drains this player's bounded input, advances movement in the player phase, offers one
 * indivisible output batch, then clears per-cycle state. [output] is deliberately non-suspending:
 * socket IO and ISAAC ownership live in the connection's writer coroutine.
 */
internal class GameLoop(
    override val playerId: Long,
    private val output: GameOutputSink,
    private val playerMovement: PlayerMovement =
        PlayerMovement(Tile(SPAWN_X, SPAWN_Y, SPAWN_PLANE), OpenCollisionMap),
    private val routeRequests: PlayerRouteRequestQueue = PlayerRouteRequestQueue(),
    private val buildArea: PlayerBuildArea = PlayerBuildArea(playerMovement.position),
    private val buttonClicks: PlayerButtonQueue,
    buttonActions: ButtonActionRegistry,
    private val playerVarps: PlayerVarps,
    private val chatInputs: PlayerChatQueue = PlayerChatQueue(),
    chatActions: ChatActionRegistry = emu.game.chat.chatActions {},
    private val chatState: PlayerChatState = PlayerChatState(),
    private val sessionControl: PlayerSessionControl,
    private val onProfileReport: (CycleProfileSnapshot) -> Unit = {},
    private val maxCycles: Int = Int.MAX_VALUE,
    cycleProcesses: List<CycleProcess> = emptyList(),
) : WorldParticipant {
    private val playerInfoState = LocalPlayerInfoState()
    private var processedCycles = 0
    private var outputSaturated = false

    init {
        require(maxCycles >= 0) { "maxCycles must be non-negative" }
    }

    private val cycle =
        GameCycle(
            buttonClicks.cycleProcesses(buttonActions) +
                chatInputs.cycleProcesses(chatActions) +
                routeRequests.cycleProcesses(playerMovement) +
                cycleProcesses +
                playerMovement.cycleProcesses() +
                CycleProcess(CyclePhase.CLIENT_OUTPUT) { tickIndex ->
                    flushClientOutput(tickIndex)
                },
        )

    /**
     * Recentres the client's 104x104 build area when movement reaches its outer two zones, then
     * flushes active-world context, the current scene-local NPC origin, local-player GPI and empty
     * NPC info as one atomic group. Appearance was established by [initialGameCycle] and is not
     * repeated; GPI carries this cycle's optional walk/run delta.
    */
    private fun flushClientOutput(tickIndex: Long) {
        val batch = GameOutputBatch.build {
            packets(playerVarps.drainClientUpdates().map { it.toProtocolMessage() })
            if (sessionControl.logoutRequested) {
                packet(Logout)
            } else {
                val position = playerMovement.position
                if (buildArea.recenterIfRequired(position)) {
                    packet(RebuildNormal(buildArea.centreZoneX, buildArea.centreZoneY))
                    logger.info {
                        "game loop: rebuilt normal scene around zone " +
                            "${buildArea.centreZoneX},${buildArea.centreZoneY} at tile ${position.x},${position.y}"
                    }
                }
                val playerInfo =
                    playerInfoState
                        .next(playerMovement.update, playerMovement.runEnabled)
                        .copy(publicChat = chatState.takePublicChat())
                packetGroup(
                    listOf(
                        SetActiveWorld(),
                        SetNpcUpdateOrigin(buildArea.localX(position.x), buildArea.localY(position.y)),
                        playerInfo,
                        NpcInfo,
                    ),
                )
                packet(ServerTickEnd)
            }
        }
        publish(batch, tickIndex)
    }

    private fun publish(batch: GameOutputBatch, tickIndex: Long) {
        if (output.offer(batch)) {
            logger.debug { "game loop: queued atomic client output for tick $tickIndex" }
        } else {
            outputSaturated = true
            logger.warn { "game loop: outbound mailbox saturated for player $playerId on tick $tickIndex" }
        }
    }

    /** Advances this player exactly once using the world's authoritative tick number. */
    override fun cycle(worldTick: Long): WorldParticipantResult {
        if (processedCycles >= maxCycles) return WorldParticipantResult.REMOVE
        cycle.tick(worldTick)
        processedCycles++
        val remove = outputSaturated || sessionControl.logoutRequested || processedCycles >= maxCycles
        logger.debug {
            when {
                outputSaturated -> "game loop: player $playerId outbound mailbox saturated"
                sessionControl.logoutRequested -> "game loop: player $playerId requested logout"
                processedCycles >= maxCycles -> "game loop: player $playerId reached test cycle cap $maxCycles"
                else -> "game loop: advanced player $playerId on world tick $worldTick"
            }
        }
        return if (remove) WorldParticipantResult.REMOVE else WorldParticipantResult.KEEP
    }

    override fun reportCycleProfile(snapshot: CycleProfileSnapshot) = onProfileReport(snapshot)
}

/** Keeps protocol-specific direction encoding outside the game simulation module. */
private fun MovementUpdate.toProtocolMovement(): ProtocolPlayerMovement? =
    when (this) {
        MovementUpdate.Idle -> null
        is MovementUpdate.Walk -> ProtocolPlayerMovement.Walk(deltaX, deltaY)
        is MovementUpdate.Run -> ProtocolPlayerMovement.Run(deltaX, deltaY)
    }

/**
 * Tracks the movement speed cached by the rev-239 client independently from each tick's positional
 * GPI update. The two-tile run opcode moves the player's true tile; MOVE_SPEED and
 * TEMP_MOVE_SPEED select the avatar's visual walk/run animation.
 */
internal class LocalPlayerInfoState {
    private var knownMoveSpeed = STATIONARY_SPEED

    fun next(update: MovementUpdate, runEnabled: Boolean): PlayerInfo {
        val selectedSpeed = if (runEnabled) RUN_SPEED else WALK_SPEED
        val moveSpeed = selectedSpeed.takeIf { it != knownMoveSpeed }
        knownMoveSpeed = selectedSpeed

        val actualSpeed =
            when (update) {
                MovementUpdate.Idle -> selectedSpeed
                is MovementUpdate.Walk -> WALK_SPEED
                is MovementUpdate.Run -> RUN_SPEED
            }
        val temporaryMoveSpeed = actualSpeed.takeIf { update != MovementUpdate.Idle && it != selectedSpeed }

        return PlayerInfo(
            movement = update.toProtocolMovement(),
            moveSpeed = moveSpeed,
            temporaryMoveSpeed = temporaryMoveSpeed,
        )
    }

    private companion object {
        const val WALK_SPEED = 1
        const val RUN_SPEED = 2
        const val STATIONARY_SPEED = 127
    }
}
