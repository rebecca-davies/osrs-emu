package emu.server.world.player

import emu.protocol.osrs239.game.message.Logout
import emu.protocol.osrs239.game.message.NpcInfo
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import emu.game.cycle.CycleProfileSnapshot
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.AdminCycleOutput
import emu.server.world.network.PlayerVarpOutput
import emu.server.world.network.PlayerInfoSnapshot
import emu.server.world.network.PlayerInfoView
import emu.server.world.network.PlayerInterfaceOutput
import emu.server.world.runtime.ConnectedPlayer
import emu.server.world.runtime.GameWorld

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
                )
            },
        )

    internal fun prepare(
        connected: ConnectedPlayer,
        view: PlayerInfoView,
        profile: CycleProfileSnapshot?,
    ) {
        val player = connected.player
        val connection = connected.connection
        connection.pendingOutput =
            when {
                !connection.isConnected -> null
                connected.writeBack.durable -> GameOutputBatch.packet(Logout)
                player.active && !player.loggingOut -> regularOutput(connected, view, profile)
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

    internal fun cleanup(world: GameWorld, connected: ConnectedPlayer) {
        val player = connected.player
        player.movement.finishCycle()
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
        profile: CycleProfileSnapshot?,
    ): GameOutputBatch {
        val player = connected.player
        val connection = connected.connection
        return GameOutputBatch.build {
            packets(
                player.interfaces
                    .drainClientUpdates()
                    .map(PlayerInterfaceOutput::message),
            )
            packets(player.varps.drainClientUpdates().map(PlayerVarpOutput::message))
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
            profile?.let { AdminCycleOutput.message(player.rank, it) }?.let(::packet)
            packet(ServerTickEnd)
        }
    }

    private companion object {
        const val MAX_LOGOUT_OUTPUT_ATTEMPTS = 3
    }
}
