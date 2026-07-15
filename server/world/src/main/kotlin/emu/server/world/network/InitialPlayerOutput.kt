package emu.server.world.network

import emu.game.content.ui.Gameframe
import emu.game.content.player.login.LoginNotice
import emu.game.map.PlayerBuildArea
import emu.server.world.entity.WorldPlayer
import emu.protocol.osrs239.game.message.HideLocOps
import emu.protocol.osrs239.game.message.HideNpcOps
import emu.protocol.osrs239.game.message.HideObjOps
import emu.protocol.osrs239.game.message.MinimapToggle
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.message.ResetAnims
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.SiteSettings
import emu.protocol.osrs239.game.message.UpdateRunEnergy
import emu.protocol.osrs239.game.message.UpdateRunWeight
import emu.protocol.osrs239.game.message.VarpReset

/** Builds the complete account-specific output batch required before world activation. */
internal class InitialPlayerOutput(
    gameframe: Gameframe,
    loginNotices: List<LoginNotice>,
) {
    private val interfaces = PlayerInterfaceOutput(gameframe)
    private val loginNotices = loginNotices.toList()

    fun build(player: WorldPlayer, localPlayerIndex: Int): GameOutputBatch {
        val position = player.movement.position
        val buildArea = PlayerBuildArea(position)
        return GameOutputBatch.build {
            packet(RebuildLogin(position.plane, position.x, position.y, localPlayerIndex))
            packet(SiteSettings())
            packets(PlayerChatOutput.messages(player.chatFilters))
            packet(HideNpcOps())
            packet(HideLocOps())
            packet(HideObjOps())
            packet(VarpReset)
            packets(PlayerVarpOutput.loginSync(player.varps))
            packetGroup(
                InitialWorldOutput.messages(
                    PlayerAppearance(name = player.displayName),
                    localPlayerIndex,
                    buildArea.localX(position.x),
                    buildArea.localY(position.y),
                ),
            )
            packets(interfaces.initialInventories())
            packets(interfaces.frameMessages())
            packets(PlayerStatOutput.initialMessages())
            packet(UpdateRunWeight())
            packet(UpdateRunEnergy())
            packet(ResetAnims)
            packet(MinimapToggle())
            packets(LoginNoticeOutput.messages(loginNotices))
            packet(ServerTickEnd)
        }
    }
}
