package emu.gateway.login

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import emu.game.cycle.CycleProfiler
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.FixedRateTickSchedule
import emu.game.cycle.GAME_TICK_MILLIS
import emu.game.cycle.GameCycle
import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerMovement as ProtocolPlayerMovement
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
    /** Scene-local X tile (`spawnX - baseX`) used by SET_NPC_UPDATE_ORIGIN. */
    private val npcOriginX: Int = LOCAL_SCENE_ORIGIN_X,
    /** Scene-local Z tile (`spawnZ - baseZ`) used by SET_NPC_UPDATE_ORIGIN. */
    private val npcOriginZ: Int = LOCAL_SCENE_ORIGIN_Z,
    private val playerMovement: PlayerMovement =
        PlayerMovement(Tile(SPAWN_X, SPAWN_Y, SPAWN_PLANE), OpenCollisionMap),
    private val routeRequests: PlayerRouteRequestQueue = PlayerRouteRequestQueue(),
    private val profileLabel: String = "connection",
    private val onProfileReport: suspend (CycleProfileSnapshot) -> Unit = {},
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
     * Flushes active-world context, NPC origin, local-player GPI and empty NPC info as one atomic
     * group, then terminates the client cycle. Appearance was established by [sendInitialGameCycle]
     * and is not repeated; GPI carries this cycle's optional walk/run delta.
     */
    private suspend fun flushClientOutput(tickIndex: Long) {
        sendPacketGroup(
            session,
            listOf(
                SetActiveWorld(),
                SetNpcUpdateOrigin(npcOriginX, npcOriginZ),
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
            delay(schedule.delayAfterTick(start, System.currentTimeMillis()))
        }
        logger.debug { "game loop: reached tick cap $maxTicks; stopping" }
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
