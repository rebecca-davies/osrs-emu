package emu.server.world.session

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.GameCycle
import emu.game.input.PlayerInput
import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonActionRegistry
import emu.game.chat.ChatActionRegistry
import emu.game.varp.PlayerVarps
import emu.server.world.player.PlayerLogoutState
import emu.server.world.player.PlayerPublicChatState
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameConnection
import emu.server.world.network.LocalPlayerInfoState
import emu.server.world.runtime.WorldParticipant
import emu.server.world.runtime.WorldParticipantResult
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.Logout
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
 * [emu.server.world.runtime.WorldRuntime]; this class cannot create an independent player clock.
 *
 * [GameCycle] drains this player's bounded input, advances movement in the player phase, offers one
 * indivisible output batch, then clears per-cycle state. [GameConnection.output] is deliberately
 * non-suspending: socket IO and ISAAC ownership live in the connection's writer coroutine.
 */
internal class GameLoop(
    override val playerId: Long,
    private val connection: GameConnection,
    private val playerMovement: PlayerMovement =
        PlayerMovement(Tile(SPAWN_X, SPAWN_Y, SPAWN_PLANE), OpenCollisionMap),
    private val buildArea: PlayerBuildArea = PlayerBuildArea(playerMovement.position),
    private val buttonActions: ButtonActionRegistry,
    private val playerVarps: PlayerVarps,
    private val chatActions: ChatActionRegistry = emu.game.chat.chatActions {},
    private val chatState: PlayerPublicChatState = PlayerPublicChatState(),
    private val logout: PlayerLogoutState,
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
            listOf(CycleProcess(CyclePhase.CLIENT_INPUT) { processInputs() }) +
                cycleProcesses +
                playerMovement.cycleProcesses() +
                CycleProcess(CyclePhase.CLIENT_OUTPUT) { tickIndex ->
                    flushClientOutput(tickIndex)
                },
        )

    private fun processInputs() {
        var latestRoute: PlayerInput.Route? = null
        connection.inputs.drain { input ->
            when (input) {
                is PlayerInput.Button -> buttonActions.dispatch(input.click)
                is PlayerInput.Chat -> chatActions.dispatch(input.input)
                is PlayerInput.Route -> latestRoute = input
            }
        }
        latestRoute?.let(::applyRoute)
    }

    private fun applyRoute(route: PlayerInput.Route) {
        val destination = Tile(route.x, route.y, playerMovement.position.plane)
        val temporaryRun = if (route.invertRun) !playerMovement.runEnabled else null
        playerMovement.routeTo(destination, temporaryRun)
    }

    /**
     * Recentres the client's 104x104 build area when movement reaches its outer two zones, then
     * flushes active-world context, the current scene-local NPC origin, local-player GPI and empty
     * NPC info as one atomic group. Appearance was established by [initialGameCycle] and is not
     * repeated; GPI carries this cycle's optional walk/run delta.
    */
    private fun flushClientOutput(tickIndex: Long) {
        val batch = GameOutputBatch.build {
            packets(playerVarps.drainClientUpdates().map { it.toProtocolMessage() })
            if (logout.requested) {
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
                        .copy(publicChat = chatState.take())
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
        if (connection.output.offer(batch)) {
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
        val remove = outputSaturated || logout.requested || processedCycles >= maxCycles
        logger.debug {
            when {
                outputSaturated -> "game loop: player $playerId outbound mailbox saturated"
                logout.requested -> "game loop: player $playerId requested logout"
                processedCycles >= maxCycles -> "game loop: player $playerId reached test cycle cap $maxCycles"
                else -> "game loop: advanced player $playerId on world tick $worldTick"
            }
        }
        return if (remove) WorldParticipantResult.REMOVE else WorldParticipantResult.KEEP
    }

    override fun reportCycleProfile(snapshot: CycleProfileSnapshot) = onProfileReport(snapshot)
}
