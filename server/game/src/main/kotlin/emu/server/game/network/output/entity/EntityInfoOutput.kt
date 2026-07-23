package emu.server.game.network.output.entity

import emu.game.entity.EntityInfoSnapshot
import emu.protocol.osrs239.game.message.entity.InfoHeadbar
import emu.protocol.osrs239.game.message.entity.InfoHeadbarType
import emu.protocol.osrs239.game.message.entity.InfoHitmark
import emu.protocol.osrs239.game.message.entity.InfoHitmarkType
import emu.protocol.osrs239.game.message.entity.InfoSpotAnimation
import emu.server.game.network.output.playerinfo.PlayerInfoVisualSnapshot

/** Projects one player's visuals once for both self and remote observer update blocks. */
internal fun EntityInfoSnapshot.toPlayerInfoVisualSnapshot(): PlayerInfoVisualSnapshot =
    PlayerInfoVisualSnapshot(
        selfHitmarks = hitmarkMessages(self = true),
        otherHitmarks = hitmarkMessages(self = false),
        headbars = headbarMessages(),
        spotAnimations = spotAnimationMessages(),
    )

/** Maps revision-neutral entity visuals to revision-239 information blocks. */
internal fun EntityInfoSnapshot.hitmarkMessages(self: Boolean): List<InfoHitmark> =
    hitmarks.map { hitmark ->
        val blocked = hitmark.damage == 0
        val type =
            when {
                blocked && self -> InfoHitmarkType.BLOCK_SELF
                blocked -> InfoHitmarkType.BLOCK_OTHER
                self -> InfoHitmarkType.DAMAGE_SELF
                else -> InfoHitmarkType.DAMAGE_OTHER
            }
        InfoHitmark(type, hitmark.damage, hitmark.delay)
    }

/** Converts the latest health value to the client's default segmented health bar. */
internal fun EntityInfoSnapshot.headbarMessages(): List<InfoHeadbar> {
    val health = healthBar ?: return emptyList()
    val fill =
        (health.current.toLong() * InfoHeadbarType.HEALTH_30_SEGMENTS / health.maximum).toInt()
    return listOf(
        InfoHeadbar(
            type = InfoHeadbarType.HEALTH_30,
            startFill = fill,
            startTime = health.delay,
            endTime = health.delay,
        ),
    )
}

internal fun EntityInfoSnapshot.spotAnimationMessages(): List<InfoSpotAnimation> =
    spotAnimations.map { animation ->
        InfoSpotAnimation(animation.slot, animation.id, animation.height, animation.delay)
    }
