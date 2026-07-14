package emu.gateway.login

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.FixedRateTickSchedule
import emu.game.cycle.GAME_TICK_MILLIS
import emu.game.cycle.GameCycle
import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.NpcInfo
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
class GameLoop(
    private val session: OutboundSession,
    private val tickInterval: Duration = TICK_INTERVAL,
    private val playerMovement: PlayerMovement =
        PlayerMovement(Tile(SPAWN_X, SPAWN_Y, SPAWN_PLANE), OpenCollisionMap),
    private val routeRequests: PlayerRouteRequestQueue = PlayerRouteRequestQueue(),
    private val buildArea: PlayerBuildArea = PlayerBuildArea(playerMovement.position),
    cycleProcesses: List<CycleProcess> = emptyList(),
) {
    private val cycle =
        GameCycle(
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
        val position = playerMovement.position
        if (buildArea.recenterIfRequired(position)) {
            session.send(RebuildNormal(buildArea.centreZoneX, buildArea.centreZoneY))
            logger.info {
                "game loop: rebuilt normal scene around zone " +
                    "${buildArea.centreZoneX},${buildArea.centreZoneY} at tile ${position.x},${position.y}"
            }
        }
        sendPacketGroup(
            session,
            listOf(
                SetActiveWorld(),
                SetNpcUpdateOrigin(buildArea.localX(position.x), buildArea.localY(position.y)),
                PlayerInfo(appearance = null, movement = playerMovement.update.toProtocolMovement()),
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
        if (initialMs > 0) delay(initialMs)
        var ticks = 0
        while (ticks < maxTicks) {
            val start = System.currentTimeMillis()
            try {
                cycle.tick()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "game loop: tick #$ticks send FAILED (server-side write error) — this closes the connection" }
                throw e
            }
            ticks++
            delay(schedule.delayAfterTick(start, System.currentTimeMillis()))
        }
        logger.debug { "game loop: reached tick cap $maxTicks; stopping" }
    }
}

/** Keeps protocol-specific direction encoding outside the game simulation module. */
private fun MovementUpdate.toProtocolMovement(): ProtocolPlayerMovement? =
    when (this) {
        MovementUpdate.Idle -> null
        is MovementUpdate.Walk -> ProtocolPlayerMovement.Walk(deltaX, deltaY)
        is MovementUpdate.Run -> ProtocolPlayerMovement.Run(deltaX, deltaY)
    }
