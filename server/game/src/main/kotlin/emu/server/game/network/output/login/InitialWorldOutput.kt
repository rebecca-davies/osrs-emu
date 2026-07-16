package emu.server.game.network.output.login

import emu.protocol.osrs239.game.message.camera.CamTargetPlayer
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.scene.SetActiveWorld
import emu.protocol.osrs239.game.message.scene.WorldEntityInfo
import emu.protocol.osrs239.game.message.zone.UpdateZoneFullFollows
import emu.transport.message.OutgoingMessage

/** Builds the atomic initial player, NPC, and scene-zone information group. */
internal object InitialWorldOutput {
    fun messages(
        appearance: PlayerAppearance,
        localPlayerIndex: Int,
        localPlayerX: Int,
        localPlayerY: Int,
    ): List<OutgoingMessage> = buildList {
        add(SetActiveWorld())
        add(SetNpcUpdateOrigin(localPlayerX, localPlayerY))
        add(WorldEntityInfo)
        add(PlayerInfo(appearance))
        add(NpcInfo)
        for ((x, z) in INITIAL_ZONE_SPIRAL) add(UpdateZoneFullFollows(x, z))
        add(CamTargetPlayer(localPlayerIndex))
    }

    private val INITIAL_ZONE_SPIRAL: List<Pair<Int, Int>> = listOf(
        48 to 48, 56 to 48, 56 to 56, 48 to 56, 40 to 56, 40 to 48, 40 to 40,
        48 to 40, 56 to 40, 64 to 40, 64 to 48, 64 to 56, 64 to 64, 56 to 64,
        48 to 64, 40 to 64, 32 to 64, 32 to 56, 32 to 48, 32 to 40, 32 to 32,
        40 to 32, 48 to 32, 56 to 32, 64 to 32, 72 to 32, 72 to 40, 72 to 48,
        72 to 56, 72 to 64, 72 to 72, 64 to 72, 56 to 72, 48 to 72, 40 to 72,
        32 to 72, 24 to 72, 24 to 64, 24 to 56, 24 to 48, 24 to 40, 24 to 32,
        24 to 24, 32 to 24, 40 to 24, 48 to 24, 56 to 24, 64 to 24, 72 to 24,
    )
}
