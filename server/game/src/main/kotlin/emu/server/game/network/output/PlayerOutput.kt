package emu.server.game.network.output

import emu.compression.HuffmanCodec
import emu.game.chat.PublicChatInput
import emu.game.cycle.CyclePhaseProfileSnapshot
import emu.game.cycle.CycleProfileSnapshot
import emu.game.player.Player
import emu.protocol.osrs239.game.message.chat.MessageGame
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.cycle.ServerTickEnd
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.player.Logout
import emu.protocol.osrs239.game.message.playerinfo.PlayerSequence
import emu.protocol.osrs239.game.message.scene.RebuildNormal
import emu.protocol.osrs239.game.message.scene.SetActiveWorld
import emu.server.game.network.connection.GameSession
import emu.server.game.network.output.playerinfo.PlayerInfoSnapshot
import emu.server.game.network.output.playerinfo.PlayerInfoView
import emu.server.game.network.output.ui.PlayerInterfaceOutput
import emu.server.game.network.output.varp.PlayerVarpOutput
import emu.server.game.world.World

/** Adapts authoritative player state into bounded rev-239 connection output. */
class PlayerOutput(
    private val world: World,
    private val huffman: HuffmanCodec,
) {
    internal fun snapshot(players: List<Player>): PlayerInfoView =
        PlayerInfoView(
            players.mapNotNull { player ->
                if (!player.active || player.loggingOut) return@mapNotNull null
                val session = world.session(player)
                PlayerInfoSnapshot(
                    index = player.index,
                    position = player.movement.position,
                    movement = player.movement.update,
                    runEnabled = player.movement.runEnabled,
                    appearance = session.appearance.message(player),
                    mapInstance = player.mapInstance,
                    publicChat = player.publicChat?.let { message -> publicChat(player, message) },
                    sequence = player.animationUpdate?.let { PlayerSequence(it.id, it.delay) },
                )
            },
        )

    internal fun prepare(
        player: Player,
        view: PlayerInfoView,
        profileMessage: MessageGame?,
    ) {
        val session = world.session(player)
        val writeBack = world.writeBack(player)
        session.pendingOutput =
            when {
                !session.isConnected -> null
                writeBack.durable -> GameOutputBatch.packet(Logout)
                player.active && !player.loggingOut -> regularOutput(player, session, view, profileMessage)
                else -> null
            }
    }

    internal fun publish(player: Player) {
        val session = world.session(player)
        val durable = world.writeBack(player).durable
        val batch = session.pendingOutput ?: return
        session.pendingOutput = null
        val accepted = session.output.offer(batch)
        if (durable && accepted) {
            session.logoutPublished = true
        } else if (!accepted) {
            if (!durable || session.recordLogoutOutputFailure() >= MAX_LOGOUT_OUTPUT_ATTEMPTS) {
                session.disconnect()
            }
            player.requestLogout()
        }
    }

    internal fun cleanup(player: Player) {
        val session = world.session(player)
        val durable = world.writeBack(player).durable
        player.finishCycle()
        if (durable && (session.logoutPublished || !session.isConnected)) world.remove(player)
    }

    private fun regularOutput(
        player: Player,
        session: GameSession,
        view: PlayerInfoView,
        profileMessage: MessageGame?,
    ): GameOutputBatch =
        GameOutputBatch.build {
            val interfaceUpdates = player.interfaces.drainClientUpdates()
            if (interfaceUpdates.isNotEmpty()) {
                packets(interfaceUpdates.map(PlayerInterfaceOutput::message))
            }
            val varpUpdates = player.varps.drainClientUpdates()
            if (varpUpdates.isNotEmpty()) {
                packets(varpUpdates.map(PlayerVarpOutput::message))
            }
            val gameMessages = player.takeGameMessages()
            if (gameMessages.isNotEmpty()) {
                packets(gameMessages.map { text -> MessageGame(MessageGame.GAME_MESSAGE, text) })
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
                    session.playerInfo.next(view),
                    NpcInfo,
                ),
            )
            profileMessage?.let(::packet)
            packet(ServerTickEnd)
        }

    internal fun profileMessage(snapshot: CycleProfileSnapshot, playerCount: Int): MessageGame {
        val hotPhases =
            snapshot.phases
                .sortedByDescending(CyclePhaseProfileSnapshot::averageNanos)
                .take(MAX_REPORTED_PHASES)
                .joinToString("/") { phase ->
                    "${phase.phase.name.lowercase()}:${millis(phase.averageNanos)}"
                }
        val text =
            "Cycle profile: players=$playerCount, cycles=${snapshot.cycles}, " +
                "avg=${millis(snapshot.averageNanos)}ms, " +
                "max=${millis(snapshot.maxNanos)}ms, lag spikes=${snapshot.lagSpikes}" +
                if (hotPhases.isEmpty()) "." else ", hot=$hotPhases ms."
        return MessageGame(MessageGame.GAME_MESSAGE, text)
    }

    private fun publicChat(
        player: Player,
        message: PublicChatInput,
    ): PlayerPublicChat =
        PlayerPublicChat(
            colour = message.colour,
            effect = message.effect,
            modIcon = player.staffModLevel.value,
            encodedText = huffman.encode(message.text),
            pattern = message.pattern,
        )

    private fun millis(nanos: Long): Double = nanos / 1_000_000.0

    private companion object {
        const val MAX_LOGOUT_OUTPUT_ATTEMPTS = 3
        const val MAX_REPORTED_PHASES = 3
    }
}
