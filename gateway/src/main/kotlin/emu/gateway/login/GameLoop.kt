package emu.gateway.login

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
import emu.gateway.game.PlayerSessionControl
import emu.gateway.game.PlayerChatState
import emu.gateway.world.WorldParticipant
import emu.gateway.world.WorldParticipantResult
import emu.netcore.pipeline.OutboundSession
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
 * [emu.gateway.world.WorldRuntime]; this class cannot create an independent player clock.
 *
 * [GameCycle] drains this player's bounded input, advances movement in the player phase, flushes
 * update info in client output, then clears per-cycle state. [session] reuses the shared
 * registry/ISAAC write path verbatim.
 */
internal class GameLoop(
    override val playerId: Long,
    private val session: OutboundSession,
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
    private val onProfileReport: suspend (CycleProfileSnapshot) -> Unit = {},
    private val maxCycles: Int = Int.MAX_VALUE,
    cycleProcesses: List<CycleProcess> = emptyList(),
) : WorldParticipant {
    private val playerInfoState = LocalPlayerInfoState()
    private var processedCycles = 0

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
     * NPC info as one atomic group. Appearance was established by [sendInitialGameCycle] and is not
     * repeated; GPI carries this cycle's optional walk/run delta.
    */
    private suspend fun flushClientOutput(tickIndex: Long) {
        for (varp in playerVarps.drainClientUpdates()) session.send(varp.toProtocolMessage())
        if (sessionControl.logoutRequested) {
            session.send(Logout)
            logger.info { "game loop: sent clean logout response on tick $tickIndex" }
            return
        }

        val position = playerMovement.position
        if (buildArea.recenterIfRequired(position)) {
            session.send(RebuildNormal(buildArea.centreZoneX, buildArea.centreZoneY))
            logger.info {
                "game loop: rebuilt normal scene around zone " +
                    "${buildArea.centreZoneX},${buildArea.centreZoneY} at tile ${position.x},${position.y}"
            }
        }
        val playerInfo =
            playerInfoState
                .next(playerMovement.update, playerMovement.runEnabled)
                .copy(publicChat = chatState.takePublicChat())
        sendPacketGroup(
            session,
            listOf(
                SetActiveWorld(),
                SetNpcUpdateOrigin(buildArea.localX(position.x), buildArea.localY(position.y)),
                playerInfo,
                NpcInfo,
            ),
        )
        session.send(ServerTickEnd)
        logger.debug { "game loop: sent atomic world group + SERVER_TICK_END for tick $tickIndex" }
    }

    /** Advances this player exactly once using the world's authoritative tick number. */
    override suspend fun cycle(worldTick: Long): WorldParticipantResult {
        if (processedCycles >= maxCycles) return WorldParticipantResult.REMOVE
        cycle.tick(worldTick)
        processedCycles++
        val remove = sessionControl.logoutRequested || processedCycles >= maxCycles
        logger.debug {
            when {
                sessionControl.logoutRequested -> "game loop: player $playerId requested logout"
                processedCycles >= maxCycles -> "game loop: player $playerId reached test cycle cap $maxCycles"
                else -> "game loop: advanced player $playerId on world tick $worldTick"
            }
        }
        return if (remove) WorldParticipantResult.REMOVE else WorldParticipantResult.KEEP
    }

    override suspend fun reportCycleProfile(snapshot: CycleProfileSnapshot) = onProfileReport(snapshot)
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
