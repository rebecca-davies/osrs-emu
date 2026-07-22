package emu.server.game.world.cycle

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProfiler
import emu.game.player.Player
import emu.server.game.network.output.PlayerOutput
import emu.server.game.persistence.CharacterWriteBackException
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.player.PlayerLifecycle
import emu.server.game.world.player.action.PlayerActions
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Runs the global RuneScape phases across every player in deterministic order. */
class WorldCycle(
    private val world: World,
    private val commands: WorldCommandQueue,
    private val actions: PlayerActions,
    private val playerPhase: PlayerPhase,
    private val lifecycle: PlayerLifecycle,
    private val output: PlayerOutput,
) {
    private val phasePlayers = ArrayList<Player>(PlayerCapacity.PER_WORLD)
    private val profiler = CycleProfiler()

    fun tick(worldTick: Long) {
        require(worldTick >= 0) { "world tick must be non-negative" }
        val cycleStartedAt = System.nanoTime()
        try {
            profiler.measure(CyclePhase.WORLD) {
                world.retryMapAreaRequests()
                commands.drain(world)
                playerPhase.begin(worldTick)
            }
            profiler.measure(CyclePhase.CLIENT_INPUT) {
                phasePlayers.clear()
                world.collectActivePlayers(phasePlayers)
                forEachPlayer(CyclePhase.CLIENT_INPUT, phasePlayers) { player ->
                    world.drainActions(player) { action -> actions.apply(player, action) }
                    world.resolveRoute(player)
                }
            }
            profiler.measure(CyclePhase.PLAYER) {
                phasePlayers.clear()
                world.collectCyclePlayers(phasePlayers)
                runPlayerPhase(phasePlayers)
            }
            profiler.measure(CyclePhase.LOGOUT) {
                phasePlayers.clear()
                world.collectAllPlayers(phasePlayers)
                forEachPlayer(CyclePhase.LOGOUT, phasePlayers, lifecycle::logout)
            }
            profiler.measure(CyclePhase.LOGIN) { runLogins() }
            profiler.measure(CyclePhase.INFO) {
                phasePlayers.clear()
                world.collectAllPlayers(phasePlayers)
                prepareOutput(phasePlayers)
            }
            profiler.measure(CyclePhase.CLIENT_OUTPUT) {
                forEachPlayer(CyclePhase.CLIENT_OUTPUT, phasePlayers, output::publish)
            }
            profiler.measure(CyclePhase.CLEANUP) {
                forEachPlayer(CyclePhase.CLEANUP, phasePlayers, output::cleanup)
                world.clearCycleProfile()
            }
        } finally {
            phasePlayers.clear()
            val cycleFinishedAt = System.nanoTime()
            val profile = profiler.record(cycleFinishedAt - cycleStartedAt, cycleFinishedAt)
            if (profile.lagSpike) logger.warn { "world: tick $worldTick exceeded its cycle budget" }
            profile.snapshot?.let(world::recordCycleProfile)
        }
    }

    fun beginShutdown() {
        commands.close(world)
        world.requestAllLogouts()
    }

    fun shutdownStep(): Boolean =
        try {
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            runPlayerPhase(phasePlayers)
            forEachPlayer(CyclePhase.LOGOUT, phasePlayers, lifecycle::logout)
            prepareOutput(phasePlayers)
            forEachPlayer(CyclePhase.CLIENT_OUTPUT, phasePlayers, output::publish)
            forEachPlayer(CyclePhase.CLEANUP, phasePlayers, output::cleanup)
            world.isEmpty()
        } finally {
            phasePlayers.clear()
        }

    fun forceShutdown() {
        try {
            var failure: CharacterWriteBackException? = null
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            for (player in phasePlayers) {
                try {
                    lifecycle.forceSnapshot(player)
                } catch (writeFailure: CharacterWriteBackException) {
                    if (failure == null) failure = writeFailure
                } finally {
                    world.session(player).disconnect()
                    world.remove(player)
                }
            }
            failure?.let { throw it }
        } finally {
            phasePlayers.clear()
        }
    }

    private fun runPlayerPhase(players: List<Player>) {
        forEachPlayer(CyclePhase.PLAYER, players) { player ->
            playerPhase.run(player)
            world.advanceMovement(player)
        }
    }

    private fun runLogins() {
        lifecycle.enterPendingPlayers()
        while (true) {
            val player = world.nextPendingActivation() ?: return
            runPlayer(CyclePhase.LOGIN, player) { activating ->
                lifecycle.login(activating)
                if (activating.active) world.prepareCurrentMapArea(activating)
            }
        }
    }

    private fun prepareOutput(players: List<Player>) {
        val view = output.snapshot(players)
        val profileMessage = world.cycleProfile?.let { output.profileMessage(it, view.playerCount) }
        forEachPlayer(CyclePhase.INFO, players) { player ->
            output.prepare(player, view, profileMessage)
        }
    }

    private fun forEachPlayer(
        phase: CyclePhase,
        players: List<Player>,
        action: (Player) -> Unit,
    ) {
        for (player in players) runPlayer(phase, player, action)
    }

    private fun runPlayer(
        phase: CyclePhase,
        player: Player,
        action: (Player) -> Unit,
    ) {
        try {
            action(player)
        } catch (failure: CharacterWriteBackException) {
            throw failure
        } catch (failure: Exception) {
            logger.error(failure) {
                "world: player ${player.id} failed during ${phase.name.lowercase()}; requesting logout"
            }
            player.requestLogout()
        }
    }
}

private inline fun CycleProfiler.measure(phase: CyclePhase, action: () -> Unit) {
    val startedAt = System.nanoTime()
    try {
        action()
    } finally {
        recordPhase(phase, System.nanoTime() - startedAt)
    }
}
