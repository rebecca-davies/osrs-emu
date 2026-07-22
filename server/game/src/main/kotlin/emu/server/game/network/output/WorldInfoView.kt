package emu.server.game.network.output

import emu.server.game.network.output.npcinfo.NpcInfoView
import emu.server.game.network.output.playerinfo.PlayerInfoView

/** Player and NPC views frozen once for every observer in one information phase. */
internal data class WorldInfoView(
    val players: PlayerInfoView,
    val npcs: NpcInfoView,
) {
    val playerCount: Int
        get() = players.playerCount
}
