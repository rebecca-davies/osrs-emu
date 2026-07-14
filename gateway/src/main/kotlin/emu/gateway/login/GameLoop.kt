package emu.gateway.login

import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

/**
 * The authentic OSRS server cycle length: one game tick is 600ms (LostCity `World.TICKRATE`, see
 * docs/superpowers/research/2026-07-14-tick-cycle-queue-architecture.md §1.1).
 */
val TICK_INTERVAL: Duration = 600.milliseconds

/**
 * The per-connection game tick loop — the milestone-5 seed of the multi-player `World` tick.
 *
 * A freshly logged-in client only stays in-game while the server keeps feeding it a PLAYER_INFO
 * (GPI) packet **every** cycle; if the server sends the initial scene and then goes silent the
 * client starves and drops the connection ~130ms later (the milestone-4 bug — see the research doc
 * §3.2/§6.5). This loop is that per-tick heartbeat: each [tick] builds and sends this connection's
 * PLAYER_INFO, and [run] fires a tick immediately and then every [tickInterval], drift-corrected so
 * the average cadence holds even when a tick runs long (the LostCity/void scheduler, research §1.2).
 *
 * It is deliberately shaped like a single-connection slice of the real `World`: [tick] is the one
 * place per-cycle work happens (today just "build info + flush"; later it grows the LostCity phase
 * order — drain inbound queue, run player/npc logic, build NPC-info + zone deltas), and [session]
 * reuses the shared [OutboundSession] → [emu.netcore.pipeline.writePacket] → ISAAC path verbatim, so
 * every server->client packet advances the outbound keystream exactly once for its opcode and stays
 * in lockstep with the client. Single-threaded per connection is fine for now; promoting this to the
 * authoritative single-thread world dispatcher is a later, mechanical move (research §6.1/§6.2).
 */
class GameLoop(
    private val session: OutboundSession,
    private val tickInterval: Duration = TICK_INTERVAL,
    /** Scene-local X tile (`spawnX - baseX`) used by SET_NPC_UPDATE_ORIGIN. */
    private val npcOriginX: Int = LOCAL_SCENE_ORIGIN_X,
    /** Scene-local Z tile (`spawnZ - baseZ`) used by SET_NPC_UPDATE_ORIGIN. */
    private val npcOriginZ: Int = LOCAL_SCENE_ORIGIN_Z,
) {
    /**
     * One steady-state game cycle: declare an atomic group containing active-world context, NPC
     * origin, idle local-player GPI and empty NPC info, then terminate the cycle. The appearance was
     * established by [sendInitialGameCycle], so repeating it here would be an incorrect second
     * extended-info update. The group boundary matches every post-login cycle in the real capture.
     */
    suspend fun tick(tickIndex: Int) {
        sendPacketGroup(
            session,
            listOf(
                SetActiveWorld(),
                SetNpcUpdateOrigin(npcOriginX, npcOriginZ),
                PlayerInfo(appearance = null),
                NpcInfo,
            ),
        )
        session.send(ServerTickEnd)
        logger.debug { "game loop: sent atomic idle world group + SERVER_TICK_END for tick $tickIndex" }
    }

    /**
     * Runs [tick] immediately, then re-schedules itself every [tickInterval] with drift correction
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
        if (initialMs > 0) delay(initialMs)
        var nextTick = System.currentTimeMillis()
        var ticks = 0
        while (ticks < maxTicks) {
            val start = System.currentTimeMillis()
            try {
                tick(ticks)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "game loop: tick #$ticks send FAILED (server-side write error) — this closes the connection" }
                throw e
            }
            ticks++
            nextTick += intervalMs
            val drift = maxOf(0L, start - nextTick)
            delay(maxOf(0L, intervalMs - (System.currentTimeMillis() - start) - drift))
        }
        logger.debug { "game loop: reached tick cap $maxTicks; stopping" }
    }
}
