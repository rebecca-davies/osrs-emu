package emu.server.world.cycle

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProfileSnapshot
import emu.server.world.player.CharacterWriteBackException
import emu.server.world.player.PlayerActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerMovementCycleProcess
import emu.server.world.player.PlayerOutputProcess
import emu.server.world.player.PlayerScriptProcess
import emu.server.world.runtime.ConnectedPlayer
import emu.server.world.runtime.GameWorld
import emu.server.world.runtime.WorldCommandQueue
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Runs the global RuneScape phases across every player in deterministic order. */
class WorldCycle(
    private val world: GameWorld,
    private val commands: WorldCommandQueue,
    private val actions: PlayerActionProcess,
    private val scripts: PlayerScriptProcess,
    private val movement: PlayerMovementCycleProcess,
    private val lifecycle: PlayerLifecycleProcess,
    private val output: PlayerOutputProcess
) {
    fun tick(worldTick: Long) {
        require(worldTick >= 0) { "world tick must be non-negative" }
        commands.drain(world)
        actions.beginCycle()
        scripts.beginCycle(worldTick)
        processPlayers(CyclePhase.CLIENT_INPUT, rotated(world.activePlayers(), worldTick)) { connected ->
            actions.process(connected.player, connected.connection)
        }
        processPlayerPhase(world.cyclePlayers())
        processPlayers(CyclePhase.LOGOUT, world.allPlayers(), lifecycle::processLogout)
        processLogins()
        processOutput(world.allPlayers())
        world.clearCycleProfile()
    }

    private fun rotated(players: List<ConnectedPlayer>, worldTick: Long): List<ConnectedPlayer> {
        if (players.size < 2) return players
        val first = (worldTick % players.size).toInt()
        if (first == 0) return players
        return players.drop(first) + players.take(first)
    }

    fun recordCycleProfile(snapshot: CycleProfileSnapshot) {
        world.recordCycleProfile(snapshot)
    }

    fun beginShutdown() {
        commands.close(world)
        world.requestAllLogouts()
    }

    fun shutdownStep(): Boolean {
        val players = world.allPlayers()
        processPlayerPhase(players)
        processPlayers(CyclePhase.LOGOUT, players, lifecycle::processLogout)
        processOutput(players)
        return world.allPlayers().isEmpty()
    }

    fun forceShutdown() {
        var failure: CharacterWriteBackException? = null
        for (connected in world.allPlayers()) {
            try {
                lifecycle.forceSnapshot(connected)
            } catch (writeFailure: CharacterWriteBackException) {
                if (failure == null) failure = writeFailure
            } finally {
                connected.connection.disconnect()
                world.remove(connected)
            }
        }
        failure?.let { throw it }
    }

    private fun processPlayerPhase(players: List<ConnectedPlayer>) {
        processPlayers(CyclePhase.PLAYER, players) { connected ->
            val player = connected.player
            scripts.process(player)
            movement.process(player.movement)
        }
    }

    private fun processLogins() {
        lifecycle.enterPendingPlayers(world)
        while (true) {
            val connected = world.nextPendingActivation() ?: return
            processPlayer(CyclePhase.LOGIN, connected) { lifecycle.processLogin(world, it) }
        }
    }

    private fun processOutput(players: List<ConnectedPlayer>) {
        val view = output.snapshot(players)
        val profile = world.cycleProfile
        processPlayers(CyclePhase.INFO, players) { output.prepare(it, view, profile) }
        output.finishInformation(players)
        processPlayers(CyclePhase.CLIENT_OUTPUT, players, output::publish)
        processPlayers(CyclePhase.CLEANUP, players) { output.cleanup(world, it) }
    }

    private fun processPlayers(
        phase: CyclePhase,
        players: List<ConnectedPlayer>,
        process: (ConnectedPlayer) -> Unit,
    ) {
        for (connected in players) processPlayer(phase, connected, process)
    }

    private fun processPlayer(
        phase: CyclePhase,
        connected: ConnectedPlayer,
        process: (ConnectedPlayer) -> Unit,
    ) {
        try {
            process(connected)
        } catch (failure: CharacterWriteBackException) {
            throw failure
        } catch (failure: Exception) {
            val player = connected.player
            logger.error(failure) {
                "world: player ${player.id} failed during ${phase.name.lowercase()}; requesting logout"
            }
            player.requestLogout()
        }
    }
}
