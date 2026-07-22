package emu.server.game.network.output.playerinfo

import emu.game.player.Player
import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterGender
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerBody

/** Caches revision-239 appearance until visual state or combat level changes. */
internal class PlayerAppearanceOutput(player: Player) {
    private var source = player.appearance
    private var combatRevision = player.stats.combatRevision
    private var cached = build(player)

    fun message(player: Player): PlayerAppearance {
        val appearance = player.appearance
        if (source !== appearance || combatRevision != player.stats.combatRevision) {
            source = appearance
            combatRevision = player.stats.combatRevision
            cached = build(player)
        }
        return cached
    }

    private fun build(player: Player): PlayerAppearance {
        val appearance = player.appearance
        val body = appearance.bodyKits
        val equipment = MutableList(PlayerAppearance.EQUIPMENT_SLOT_COUNT) { 0 }
        equipment[TORSO_SLOT] = PlayerAppearance.identityKit(body.torso)
        equipment[ARMS_SLOT] = PlayerAppearance.identityKit(body.arms)
        equipment[LEGS_SLOT] = PlayerAppearance.identityKit(body.legs)
        equipment[HAIR_SLOT] = PlayerAppearance.identityKit(body.hair)
        equipment[HANDS_SLOT] = PlayerAppearance.identityKit(body.hands)
        equipment[FEET_SLOT] = PlayerAppearance.identityKit(body.feet)
        equipment[JAW_SLOT] = PlayerAppearance.identityKit(body.jaw)
        return PlayerAppearance(
            gender =
                when (appearance.gender) {
                    CharacterGender.MALE -> PlayerAppearance.GENDER_MALE
                    CharacterGender.FEMALE -> PlayerAppearance.GENDER_FEMALE
                },
            body =
                PlayerBody(
                    equipment = equipment,
                    colors =
                        listOf(
                            appearance.colors.hair,
                            appearance.colors.torso,
                            appearance.colors.legs,
                            appearance.colors.feet,
                            appearance.colors.skin,
                        ),
                ),
            name = player.displayName,
            combatLevel = player.stats.combatLevel(),
        )
    }

    private companion object {
        const val TORSO_SLOT = 4
        const val ARMS_SLOT = 6
        const val LEGS_SLOT = 7
        const val HAIR_SLOT = 8
        const val HANDS_SLOT = 9
        const val FEET_SLOT = 10
        const val JAW_SLOT = 11
    }
}
