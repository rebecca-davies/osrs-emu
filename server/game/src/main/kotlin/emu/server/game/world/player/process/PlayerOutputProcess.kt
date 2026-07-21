package emu.server.game.world.player.process

import emu.game.cycle.CycleProfileSnapshot
import emu.protocol.osrs239.game.message.chat.MessageGame
import emu.protocol.osrs239.game.message.cycle.ServerTickEnd
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.player.Logout
import emu.protocol.osrs239.game.message.playerinfo.PlayerSequence
import emu.protocol.osrs239.game.message.scene.RebuildNormal
import emu.protocol.osrs239.game.message.scene.SetActiveWorld
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.playerinfo.PlayerInfoSnapshot
import emu.server.game.network.output.playerinfo.PlayerInfoView
import emu.server.game.network.output.ui.PlayerInterfaceOutput
import emu.server.game.network.output.varp.PlayerVarpOutput
import emu.server.game.world.World
import emu.server.game.world.player.ConnectedPlayer

/** Builds all player info before publishing any connection output, then clears cycle state. */
class PlayerOutputProcess {
    internal fun snapshot(players: List<ConnectedPlayer>): PlayerInfoView =
        PlayerInfoView(
            players.mapNotNull { connected ->
                val player = connected.player
                val connection = connected.connection
                if (!player.active || player.loggingOut) return@mapNotNull null
                PlayerInfoSnapshot(
                    index = connection.playerIndex,
                    position = player.movement.position,
                    movement = player.movement.update,
                    runEnabled = player.movement.runEnabled,
                    appearance = connection.appearance,
                    publicChat = connection.publicChat.current(),
                    sequence = player.animationUpdate?.let { PlayerSequence(it.id, it.delay) },
                )
            },
        )

    internal fun prepare(
        connected: ConnectedPlayer,
        view: PlayerInfoView,
        profileMessage: MessageGame?,
    ) {
        val player = connected.player
        val connection = connected.connection
        connection.pendingOutput =
            when {
                !connection.isConnected -> null
                connected.writeBack.durable -> GameOutputBatch.packet(Logout)
                player.active && !player.loggingOut -> regularOutput(connected, view, profileMessage)
                else -> null
            }
    }

    internal fun finishInformation(players: List<ConnectedPlayer>) {
        players.forEach { it.connection.publicChat.clear() }
    }

    internal fun publish(connected: ConnectedPlayer) {
        val connection = connected.connection
        val batch = connection.pendingOutput ?: return
        connection.pendingOutput = null
        val accepted = connection.output.offer(batch)
        if (connected.writeBack.durable && accepted) {
            connection.logoutPublished = true
        } else if (!accepted) {
            if (
                !connected.writeBack.durable ||
                    connection.recordLogoutOutputFailure() >= MAX_LOGOUT_OUTPUT_ATTEMPTS
            ) {
                connection.disconnect()
            }
            connected.player.requestLogout()
        }
    }

    internal fun cleanup(world: World, connected: ConnectedPlayer) {
        val player = connected.player
        player.finishCycle()
        if (
            connected.writeBack.durable &&
                (connected.connection.logoutPublished || !connected.connection.isConnected)
        ) {
            world.remove(connected)
        }
    }

    private fun regularOutput(
        connected: ConnectedPlayer,
        view: PlayerInfoView,
        profileMessage: MessageGame?,
    ): GameOutputBatch {
        val player = connected.player
        val connection = connected.connection
        return GameOutputBatch.build {
            val interfaceUpdates = player.interfaces.drainClientUpdates()
            if (interfaceUpdates.isNotEmpty()) {
                packets(interfaceUpdates.map(PlayerInterfaceOutput::message))
            }
            val varpUpdates = player.varps.drainClientUpdates()
            if (varpUpdates.isNotEmpty()) {
                packets(varpUpdates.map(PlayerVarpOutput::message))
            }
            val gameMessages = connection.drainGameMessages()
            if (gameMessages.isNotEmpty()) {
                packets(
                    gameMessages.map { text ->
                        MessageGame(MessageGame.GAME_MESSAGE, text)
                    },
                )
            }
            val position = player.movement.position
            if (player.buildArea.recenterIfRequired(position)) {
                packet(RebuildNormal(player.buildArea.centreZoneX, player.buildArea.centreZoneY))
            }
            packetGroup(
                listOf(
                    SetActiveWorld(),
                    SetNpcUpdateOrigin(
                        player.buildArea.localX(position.x),
                        player.buildArea.localY(position.y),
                    ),
                    connection.playerInfo.next(view),
                    NpcInfo,
                ),
            )
            profileMessage?.let(::packet)
            packet(ServerTickEnd)
        }
    }

    internal fun profileMessage(snapshot: CycleProfileSnapshot, playerCount: Int): MessageGame {
        val text =
            "Cycle profile: players=$playerCount, cycles=${snapshot.cycles}, " +
                "avg=${millis(snapshot.averageNanos)}ms, " +
                "max=${millis(snapshot.maxNanos)}ms, lag spikes=${snapshot.lagSpikes}."
        return MessageGame(MessageGame.GAME_MESSAGE, text)
    }

    private fun millis(nanos: Long): Double = nanos / 1_000_000.0

    private companion object {
        const val MAX_LOGOUT_OUTPUT_ATTEMPTS = 3
    }
}
