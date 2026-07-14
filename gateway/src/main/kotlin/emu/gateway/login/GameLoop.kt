package emu.gateway.login

import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SetActiveWorld
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
    private val localAppearance: PlayerAppearance = PlayerAppearance(),
) {
    /**
     * One game cycle for this connection: build and send the per-tick PLAYER_INFO heartbeat, plus a
     * SERVER_TICK_END. The **first** cycle ([tickIndex] 0) carries the local player's appearance
     * extended-info so the client can build the avatar and complete login — the real server
     * establishes the local avatar in its first post-login cycle, and without it the client reaches
     * LOGGED_IN then drops (see docs/.../2026-07-14-real-rev239-login-capture.md). Every later cycle
     * sends the minimal appearance-less idle GPI (the local player is already established), which is
     * the steady heartbeat the client's IN_GAME state machine needs. Sending consumes one outbound
     * ISAAC int per packet (for the opcode, via [OutboundSession.send] → `writePacket`), keeping the
     * client's decryptor in step.
     */
    suspend fun tick(tickIndex: Int) {
        // DIAGNOSTIC toggle (milestone-5 bisection): EMU_NO_APPEARANCE=1 sends the minimal
        // appearance-less GPI even on tick 0, to A/B whether the appearance extended-info block
        // is implicated in the post-login drop.
        val appearance = if (tickIndex == 0 && System.getenv("EMU_NO_APPEARANCE") != "1") localAppearance else null
        // Set the root world active FIRST, before player/npc info — the rev-235+ world-entity
        // system processes those relative to the active world (rsmod RspCycle.flush order).
        session.send(SetActiveWorld())
        session.send(PlayerInfo(appearance))
        session.send(ServerTickEnd)
        logger.debug { "game loop: sent tick $tickIndex (SET_ACTIVE_WORLD op47 + PLAYER_INFO op28${if (appearance != null) " +appearance" else ""} + SERVER_TICK_END op83)" }
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
