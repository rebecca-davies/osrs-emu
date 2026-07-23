package emu.server.game.network.output.npcinfo

import emu.game.npc.Npc
import emu.protocol.osrs239.game.message.npc.NpcInfoUpdate
import emu.protocol.osrs239.game.message.npc.NpcSequence
import emu.server.game.network.output.entity.headbarMessages
import emu.server.game.network.output.entity.hitmarkMessages
import emu.server.game.network.output.entity.spotAnimationMessages

/** Builds one immutable protocol visual update shared by every NPC observer in an information phase. */
internal fun Npc.toInfoUpdateSnapshot(): NpcInfoUpdate? {
    val info = infoSnapshot()
    val sequence = animationUpdate?.let { NpcSequence(it.id, it.delay) }
    if (info == null && sequence == null) return null
    return NpcInfoUpdate(
        sequence = sequence,
        hitmarks = info?.hitmarkMessages(self = false).orEmpty(),
        headbars = info?.headbarMessages().orEmpty(),
        spotAnimations = info?.spotAnimationMessages().orEmpty(),
    )
}
