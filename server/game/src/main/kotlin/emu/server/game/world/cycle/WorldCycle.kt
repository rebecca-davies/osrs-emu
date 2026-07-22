package emu.server.game.world.cycle

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProfileSnapshot
import emu.server.game.persistence.CharacterWriteBackException
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.player.ConnectedPlayer
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerMainProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Runs the global RuneScape phases across every player in deterministic order. */
class WorldCycle(
    private val world: World,
    private val commands: WorldCommandQueue,
    private val actions: PlayerActionProcess,
    private val playerMain: PlayerMainProcess,
    private val lifecycle: PlayerLifecycleProcess,
    private val output: PlayerOutputProcess,
) {
    private val phasePlayers = ArrayList<ConnectedPlayer>(PlayerCapacity.PER_WORLD)

    fun tick(worldTick: Long) {
        require(worldTick >= 0) { "world tick must be non-negative" }
        try {
            commands.drain(world)
            playerMain.beginCycle(worldTick)
            phasePlayers.clear()
            world.collectActivePlayers(phasePlayers)
            processPlayers(CyclePhase.CLIENT_INPUT, phasePlayers) { connected ->
                actions.process(connected.player, connected.connection)
            }
            phasePlayers.clear()
            world.collectCyclePlayers(phasePlayers)
            processPlayerPhase(phasePlayers)
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            processPlayers(CyclePhase.LOGOUT, phasePlayers, process = lifecycle::processLogout)
            processLogins()
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            processOutput(phasePlayers)
            world.clearCycleProfile()
        } finally {
            phasePlayers.clear()
        }
    }

    fun recordCycleProfile(snapshot: CycleProfileSnapshot) {
        world.recordCycleProfile(snapshot)
    }

    fun beginShutdown() {
        commands.close(world)
        world.requestAllLogouts()
    }

    fun shutdownStep(): Boolean {
        return try {
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            processPlayerPhase(phasePlayers)
            processPlayers(CyclePhase.LOGOUT, phasePlayers, process = lifecycle::processLogout)
            processOutput(phasePlayers)
            world.isEmpty()
        } finally {
            phasePlayers.clear()
        }
    }

    fun forceShutdown() {
        try {
            var failure: CharacterWriteBackException? = null
            phasePlayers.clear()
            world.collectAllPlayers(phasePlayers)
            for (connected in phasePlayers) {
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
        } finally {
            phasePlayers.clear()
        }
    }

    private fun processPlayerPhase(players: List<ConnectedPlayer>) {
        processPlayers(CyclePhase.PLAYER, players) { connected ->
            playerMain.process(connected.player)
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
        val profileMessage = world.cycleProfile?.let { output.profileMessage(it, view.playerCount) }
        processPlayers(CyclePhase.INFO, players) { output.prepare(it, view, profileMessage) }
        output.finishInformation(players)
        processPlayers(CyclePhase.CLIENT_OUTPUT, players, process = output::publish)
        processPlayers(CyclePhase.CLEANUP, players) { output.cleanup(world, it) }
    }

    private fun processPlayers(
        phase: CyclePhase,
        players: List<ConnectedPlayer>,
        process: (ConnectedPlayer) -> Unit,
    ) {
        for (connected in players) {
            processPlayer(phase, connected, process)
        }
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
