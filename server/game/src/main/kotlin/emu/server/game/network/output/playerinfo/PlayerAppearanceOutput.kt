package emu.server.game.network.output.playerinfo

import emu.game.player.Player
import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterGender
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerBody

/** Returns a rev-239 value whose object identity changes exactly when the character appearance changes. */
internal class PlayerAppearanceOutput(player: Player) {
    private var source = player.appearance
    private var cached = build(source, player.displayName)

    fun message(player: Player): PlayerAppearance {
        val appearance = player.appearance
        if (source !== appearance) {
            source = appearance
            cached = build(appearance, player.displayName)
        }
        return cached
    }

    private fun build(appearance: CharacterAppearance, displayName: String): PlayerAppearance {
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
            name = displayName,
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
