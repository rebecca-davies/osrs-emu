package emu.server.game.network.output.login

import emu.game.content.player.login.LoginNotice
import emu.game.content.ui.gameframe.Gameframe
import emu.game.player.Player
import emu.protocol.osrs239.game.message.client.SiteSettings
import emu.protocol.osrs239.game.message.cycle.ServerTickEnd
import emu.protocol.osrs239.game.message.npc.HideNpcOps
import emu.protocol.osrs239.game.message.player.MinimapToggle
import emu.protocol.osrs239.game.message.player.ResetAnims
import emu.protocol.osrs239.game.message.player.UpdateRunEnergy
import emu.protocol.osrs239.game.message.player.UpdateRunWeight
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.scene.RebuildLogin
import emu.protocol.osrs239.game.message.varp.VarpReset
import emu.protocol.osrs239.game.message.zone.HideLocOps
import emu.protocol.osrs239.game.message.zone.HideObjOps
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.chat.PlayerChatOutput
import emu.server.game.network.output.stat.PlayerStatOutput
import emu.server.game.network.output.ui.PlayerInterfaceOutput
import emu.server.game.network.output.varp.PlayerVarpOutput

/** Builds the complete account-specific output batch required before world activation. */
internal class InitialPlayerOutput(
    gameframe: Gameframe,
    loginNotices: List<LoginNotice>,
) {
    private val interfaces = PlayerInterfaceOutput(gameframe)
    private val loginNotices = loginNotices.toList()

    fun build(
        player: Player,
        appearance: PlayerAppearance,
    ): GameOutputBatch {
        val position = player.movement.position
        val buildArea = player.buildArea
        return GameOutputBatch.build {
            packet(RebuildLogin(position.plane, position.x, position.y, player.index))
            packet(SiteSettings())
            packets(PlayerChatOutput.messages(player.chatFilters))
            packet(HideNpcOps())
            packet(HideLocOps())
            packet(HideObjOps())
            packet(VarpReset)
            packets(PlayerVarpOutput.loginSync(player.varps))
            packetGroup(
                InitialWorldOutput.messages(
                    appearance,
                    player.index,
                    buildArea.localX(position.x),
                    buildArea.localY(position.y),
                ),
            )
            packets(interfaces.initialInventories(player))
            packets(interfaces.frameMessages())
            packets(PlayerStatOutput.messages(player.stats.loginSync()))
            packet(UpdateRunWeight())
            packet(UpdateRunEnergy())
            packet(ResetAnims)
            packet(MinimapToggle())
            packets(LoginNoticeOutput.messages(loginNotices))
            packet(ServerTickEnd)
        }
    }
}
