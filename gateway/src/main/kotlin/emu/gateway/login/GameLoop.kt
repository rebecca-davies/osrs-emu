package emu.gateway.login

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.CycleProfiler
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.FixedRateTickSchedule
import emu.game.cycle.GAME_TICK_MILLIS
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
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

/**
 * The authentic OSRS server cycle length: one game tick is 600ms (LostCity `World.TICKRATE`; see
 * `2026-07-14-lostcity-cycle-queues-blurite-pathfinding.md`).
 */
val TICK_INTERVAL: Duration = GAME_TICK_MILLIS.milliseconds

/**
 * The per-connection game tick loop — the milestone-5 seed of the multi-player `World` tick.
 *
 * A freshly logged-in client only stays in-game while the server keeps feeding it a PLAYER_INFO
 * (GPI) packet **every** cycle. [run] fires a cycle immediately and then every [tickInterval],
 * drift-corrected so the average cadence holds even when a cycle runs long.
 *
 * It is a single-connection slice of the real `World`: [GameCycle] drains bounded client input,
 * advances movement in the player phase, flushes update info in client output, then clears
 * per-cycle state. [session] reuses the shared registry/ISAAC write path verbatim.
 */
internal class GameLoop(
    private val session: OutboundSession,
    private val tickInterval: Duration = TICK_INTERVAL,
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
    private val profileLabel: String = "connection",
    private val onProfileReport: suspend (CycleProfileSnapshot) -> Unit = {},
    cycleProcesses: List<CycleProcess> = emptyList(),
) {
    private val playerInfoState = LocalPlayerInfoState()

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

    /**
     * Runs one cycle immediately, then re-schedules every [tickInterval] with drift correction
     * (`delay = interval - work - accumulated_drift`, floored at 0 — the LostCity/void formula): if
     * a tick blows the budget the next fires immediately rather than letting error accumulate.
     *
     * Loops until cancelled (client disconnect / idle timeout stops it from outside — see
     * [runGameStage]) or until [maxTicks] ticks have run. [maxTicks] exists only so tests can drive
     * a bounded number of heartbeats without a real-time sleep; production leaves it unbounded.
     */
    suspend fun run(maxTicks: Int = Int.MAX_VALUE) {
        val intervalMs = System.getenv("EMU_TICK_INTERVAL_MS")?.toLongOrNull() ?: tickInterval.inWholeMilliseconds
        val initialMs = System.getenv("EMU_TICK_INITIAL_MS")?.toLongOrNull() ?: 0L
        require(maxTicks >= 0) { "maxTicks must be non-negative" }
        val schedule = FixedRateTickSchedule(intervalMs)
        val profiler = CycleProfiler()
        if (initialMs > 0) delay(initialMs)
        var ticks = 0
        while (ticks < maxTicks) {
            val start = System.currentTimeMillis()
            val profileStart = System.nanoTime()
            try {
                cycle.tick()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "game loop: tick #$ticks send FAILED (server-side write error) — this closes the connection" }
                throw e
            }
            val profileFinished = System.nanoTime()
            val profile = profiler.record(profileFinished - profileStart, profileFinished)
            if (profile.lagSpike) {
                logger.warn {
                    "game loop: $profileLabel tick #$ticks exceeded ${GAME_TICK_MILLIS}ms budget " +
                        "(${millis(profileFinished - profileStart)}ms)"
                }
            }
            profile.snapshot?.let { snapshot ->
                logger.info {
                    "game loop: $profileLabel ${millis(snapshot.windowNanos)}ms profile: " +
                        "cycles=${snapshot.cycles}, avg=${millis(snapshot.averageNanos)}ms, " +
                        "max=${millis(snapshot.maxNanos)}ms, lagSpikes=${snapshot.lagSpikes}"
                }
                onProfileReport(snapshot)
            }
            ticks++
            if (sessionControl.logoutRequested) break
            delay(schedule.delayAfterTick(start, System.currentTimeMillis()))
        }
        logger.debug {
            if (sessionControl.logoutRequested) "game loop: logout requested; stopping"
            else "game loop: reached tick cap $maxTicks; stopping"
        }
    }
}

private fun millis(nanos: Long): Double = nanos / 1_000_000.0

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
