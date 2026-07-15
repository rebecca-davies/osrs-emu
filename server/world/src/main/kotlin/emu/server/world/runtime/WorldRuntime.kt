package emu.server.world.runtime

import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.CycleProfiler
import emu.game.cycle.FixedRateTickSchedule
import emu.game.cycle.GAME_TICK_MILLIS
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.session.ReservationRejection
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}
private const val DEFAULT_COMMAND_CAPACITY = 1_024

/**
 * The game service's single authoritative world clock.
 *
 * Registration and removal cross into the world through a bounded command mailbox. The active
 * participant map, tick number, simulation work and profiling are all owned by [run]'s coroutine,
 * so two players can never advance on independent schedules or race the same world state.
 * Participant failures are isolated to that participant; cancellation still tears down the world.
 */
class WorldRuntime(
    tickInterval: Duration = GAME_TICK_MILLIS.milliseconds,
    commandCapacity: Int = DEFAULT_COMMAND_CAPACITY,
    private val maxPlayerIndex: Int = PlayerCapacity.PER_WORLD,
    private val profiler: CycleProfiler = CycleProfiler(),
) : WorldReservationService, WorldSessionRegistry {
    private val intervalMillis = tickInterval.inWholeMilliseconds
    private val commands: Channel<Command>
    private val started = AtomicBoolean(false)

    init {
        require(intervalMillis > 0) { "tick interval must be at least one millisecond" }
        require(commandCapacity > 0) { "command capacity must be positive" }
        require(maxPlayerIndex in 1..PlayerCapacity.PER_WORLD) { "maximum player index out of range: $maxPlayerIndex" }
        commands = Channel(commandCapacity)
    }

    /**
     * Submits [participant] for admission without allowing an unbounded producer backlog. A full
     * or closed mailbox rejects the registration immediately and completes both lifecycle signals.
     */
    override fun register(
        participant: WorldParticipant,
        startActive: Boolean,
    ): WorldRegistration {
        val playerIndex = CompletableDeferred<Int?>()
        val removal = CompletableDeferred<Unit>()
        val command = Command.Add(participant, startActive, playerIndex, removal)
        if (commands.trySend(command).isFailure) {
            playerIndex.complete(null)
            removal.complete(Unit)
            logger.warn { "world: rejected player ${participant.playerId}; command mailbox is unavailable" }
        }
        return WorldRegistration(playerIndex, removal)
    }

    /** Reserves a duplicate-checked player slot before login success is written. */
    override suspend fun reserve(
        playerId: Long,
        token: GameSessionToken,
    ): ReservationDecision {
        val result = CompletableDeferred<ReservationDecision>()
        if (commands.trySend(Command.Reserve(playerId, token, result)).isFailure) {
            result.complete(ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE))
        }
        return result.await()
    }

    /** Attaches the game participant to a previously accepted reservation. */
    override fun attach(
        token: GameSessionToken,
        participant: WorldParticipant,
        startActive: Boolean,
    ): WorldRegistration {
        val playerIndex = CompletableDeferred<Int?>()
        val removal = CompletableDeferred<Unit>()
        if (commands.trySend(Command.Attach(token, participant, startActive, playerIndex, removal)).isFailure) {
            playerIndex.complete(null)
            removal.complete(Unit)
        }
        return WorldRegistration(playerIndex, removal)
    }

    /** Releases a reservation that did not reach the game stage. */
    override suspend fun release(token: GameSessionToken) {
        try {
            commands.send(Command.Release(token))
        } catch (_: ClosedSendChannelException) {
            logger.debug { "world: ignored reservation release after shutdown" }
        }
    }

    /** Requests removal at the next world command boundary. */
    override suspend fun remove(playerId: Long) {
        try {
            commands.send(Command.Remove(playerId))
        } catch (_: ClosedSendChannelException) {
            logger.debug { "world: ignored removal for player $playerId after shutdown" }
        }
    }

    /** Makes a paused, admitted participant eligible for the next cycle. */
    override suspend fun activate(playerId: Long) {
        try {
            commands.send(Command.Activate(playerId))
        } catch (_: ClosedSendChannelException) {
            logger.debug { "world: ignored activation for player $playerId after shutdown" }
        }
    }

    /**
     * Advances every admitted participant from one fixed-rate clock. [maxTicks] is a deterministic
     * test seam; production leaves it unbounded. The first cycle runs immediately.
     */
    suspend fun run(maxTicks: Long = Long.MAX_VALUE) {
        require(maxTicks >= 0) { "maxTicks must be non-negative" }
        check(started.compareAndSet(false, true)) { "world runtime can only be started once" }
        val active = linkedMapOf<Long, ActiveParticipant>()
        val reservations = linkedMapOf<GameSessionToken, ReservedPlayer>()
        val playerIndexes = PlayerIndexAllocator(maxPlayerIndex)
        val schedule = FixedRateTickSchedule(intervalMillis)
        var worldTick = 0L
        try {
            while (worldTick < maxTicks) {
                val startedAtMillis = System.currentTimeMillis()
                val profileStartedAt = System.nanoTime()
                drainCommands(active, reservations, playerIndexes)
                runParticipantCycles(active, playerIndexes, worldTick)
                val profileFinishedAt = System.nanoTime()
                val cycleDurationNanos = profileFinishedAt - profileStartedAt
                publishProfile(
                    active,
                    playerIndexes,
                    worldTick,
                    cycleDurationNanos,
                    profiler.record(cycleDurationNanos, profileFinishedAt),
                )
                worldTick++
                if (worldTick < maxTicks) {
                    val delayMillis = schedule.delayAfterTick(startedAtMillis, System.currentTimeMillis())
                    if (delayMillis > 0) {
                        delay(delayMillis)
                    } else {
                        yield()
                    }
                }
            }
        } finally {
            commands.close()
            active.keys.toList().forEach { removeParticipant(active, playerIndexes, it) }
            reservations.values.forEach { playerIndexes.release(it.playerIndex) }
            reservations.clear()
            rejectPendingRegistrations()
            logger.info { "world: stopped after $worldTick cycles" }
        }
    }

    private fun drainCommands(
        active: MutableMap<Long, ActiveParticipant>,
        reservations: MutableMap<GameSessionToken, ReservedPlayer>,
        playerIndexes: PlayerIndexAllocator,
    ) {
        while (true) {
            when (val command = commands.tryReceive().getOrNull() ?: return) {
                is Command.Add -> {
                    if (
                        active.containsKey(command.participant.playerId) ||
                        reservations.values.any { it.playerId == command.participant.playerId }
                    ) {
                        command.playerIndex.complete(null)
                        command.removal.complete(Unit)
                        logger.warn { "world: rejected duplicate session for player ${command.participant.playerId}" }
                    } else {
                        val playerIndex = playerIndexes.allocate()
                        if (playerIndex == null) {
                            command.playerIndex.complete(null)
                            command.removal.complete(Unit)
                            logger.warn { "world: rejected player ${command.participant.playerId}; player slots are full" }
                        } else {
                            active[command.participant.playerId] =
                                ActiveParticipant(
                                    command.participant,
                                    playerIndex,
                                    command.removal,
                                    command.startActive,
                                )
                            command.playerIndex.complete(playerIndex)
                            logger.info {
                                "world: admitted player ${command.participant.playerId} at index $playerIndex"
                            }
                        }
                    }
                }

                is Command.Reserve -> {
                    if (
                        active.containsKey(command.playerId) ||
                        reservations.values.any { it.playerId == command.playerId }
                    ) {
                        command.result.complete(ReservationDecision.Rejected(ReservationRejection.DUPLICATE))
                    } else {
                        val playerIndex = playerIndexes.allocate()
                        if (playerIndex == null) {
                            command.result.complete(ReservationDecision.Rejected(ReservationRejection.CAPACITY))
                        } else {
                            reservations[command.token] = ReservedPlayer(command.playerId, playerIndex)
                            command.result.complete(ReservationDecision.Accepted(command.token, playerIndex))
                        }
                    }
                }

                is Command.Attach -> {
                    val reservation = reservations.remove(command.token)
                    if (reservation == null || reservation.playerId != command.participant.playerId) {
                        command.playerIndex.complete(null)
                        command.removal.complete(Unit)
                    } else {
                        active[reservation.playerId] =
                            ActiveParticipant(
                                command.participant,
                                reservation.playerIndex,
                                command.removal,
                                command.startActive,
                            )
                        command.playerIndex.complete(reservation.playerIndex)
                    }
                }

                is Command.Release -> {
                    reservations.remove(command.token)?.let { playerIndexes.release(it.playerIndex) }
                }

                is Command.Activate -> active[command.playerId]?.active = true
                is Command.Remove -> removeParticipant(active, playerIndexes, command.playerId)
            }
        }
    }

    private fun runParticipantCycles(
        active: MutableMap<Long, ActiveParticipant>,
        playerIndexes: PlayerIndexAllocator,
        worldTick: Long,
    ) {
        val removals = mutableListOf<Long>()
        for ((playerId, entry) in active) {
            if (!entry.active) continue
            try {
                if (entry.participant.cycle(worldTick) == WorldParticipantResult.REMOVE) {
                    removals += playerId
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                logger.error(failure) { "world: player $playerId failed on tick $worldTick; removing session" }
                removals += playerId
            }
        }
        removals.forEach { removeParticipant(active, playerIndexes, it) }
    }

    private fun publishProfile(
        active: MutableMap<Long, ActiveParticipant>,
        playerIndexes: PlayerIndexAllocator,
        worldTick: Long,
        cycleDurationNanos: Long,
        event: emu.game.cycle.CycleProfileEvent,
    ) {
        if (event.lagSpike) {
            logger.warn {
                "world: tick $worldTick exceeded the ${GAME_TICK_MILLIS}ms budget " +
                    "(${millis(cycleDurationNanos)}ms)"
            }
        }
        val snapshot = event.snapshot ?: return
        logger.info {
            "world: ${millis(snapshot.windowNanos)}ms profile: cycles=${snapshot.cycles}, " +
                "avg=${millis(snapshot.averageNanos)}ms, max=${millis(snapshot.maxNanos)}ms, " +
                "lagSpikes=${snapshot.lagSpikes}, players=${active.values.count { it.active }}"
        }
        val removals = mutableListOf<Long>()
        for ((playerId, entry) in active) {
            if (!entry.active) continue
            try {
                entry.participant.reportCycleProfile(snapshot)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                logger.error(failure) { "world: failed to publish cycle profile to player $playerId; removing session" }
                removals += playerId
            }
        }
        removals.forEach { removeParticipant(active, playerIndexes, it) }
    }

    private fun removeParticipant(
        active: MutableMap<Long, ActiveParticipant>,
        playerIndexes: PlayerIndexAllocator,
        playerId: Long,
    ) {
        val removed = active.remove(playerId) ?: return
        playerIndexes.release(removed.playerIndex)
        removed.removal.complete(Unit)
        logger.info { "world: removed player $playerId from index ${removed.playerIndex}" }
    }

    private fun rejectPendingRegistrations() {
        while (true) {
            when (val command = commands.tryReceive().getOrNull() ?: return) {
                is Command.Add -> {
                    command.playerIndex.complete(null)
                    command.removal.complete(Unit)
                }

                is Command.Reserve -> command.result.complete(ReservationDecision.Rejected(ReservationRejection.UNAVAILABLE))
                is Command.Attach -> {
                    command.playerIndex.complete(null)
                    command.removal.complete(Unit)
                }
                is Command.Release -> Unit

                is Command.Activate -> Unit
                is Command.Remove -> Unit
            }
        }
    }

    private sealed interface Command {
        data class Add(
            val participant: WorldParticipant,
            val startActive: Boolean,
            val playerIndex: CompletableDeferred<Int?>,
            val removal: CompletableDeferred<Unit>,
        ) : Command

        data class Reserve(
            val playerId: Long,
            val token: GameSessionToken,
            val result: CompletableDeferred<ReservationDecision>,
        ) : Command

        data class Attach(
            val token: GameSessionToken,
            val participant: WorldParticipant,
            val startActive: Boolean,
            val playerIndex: CompletableDeferred<Int?>,
            val removal: CompletableDeferred<Unit>,
        ) : Command

        data class Release(val token: GameSessionToken) : Command

        data class Activate(val playerId: Long) : Command

        data class Remove(val playerId: Long) : Command
    }

    private data class ActiveParticipant(
        val participant: WorldParticipant,
        val playerIndex: Int,
        val removal: CompletableDeferred<Unit>,
        var active: Boolean,
    )

    private data class ReservedPlayer(
        val playerId: Long,
        val playerIndex: Int,
    )
}

private fun millis(nanos: Long): Double = nanos / 1_000_000.0
