package emu.server.game.network.output.playerinfo

import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.server.game.toTestPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PlayerAppearanceOutputTest {
    @Test
    fun `persisted character appearance maps into revision-239 player-info slots`() {
        val player =
            CharacterRecord(
                    id = 1,
                    displayName = "Player",
                    position = CharacterPosition(3_200, 3_200, 0),
                    playTimeSeconds = 0,
                    appearance =
                        CharacterAppearance(
                            gender = CharacterGender.FEMALE,
                            bodyKits =
                                CharacterBodyKits(
                                    hair = 55,
                                    jaw = 306,
                                    torso = 60,
                                    arms = 66,
                                    hands = 68,
                                    legs = 78,
                                    feet = 80,
                                ),
                            colors = CharacterColors(hair = 29, torso = 28, legs = 27, feet = 5, skin = 13),
                        ),
                ).toTestPlayer()

        val output = PlayerAppearanceOutput(player)
        val appearance = output.message(player)

        assertSame(appearance, output.message(player))
        assertEquals(PlayerAppearance.GENDER_FEMALE, appearance.gender)
        assertEquals("Player", appearance.name)
        assertEquals(
            listOf(
                0,
                0,
                0,
                0,
                PlayerAppearance.identityKit(60),
                0,
                PlayerAppearance.identityKit(66),
                PlayerAppearance.identityKit(78),
                PlayerAppearance.identityKit(55),
                PlayerAppearance.identityKit(68),
                PlayerAppearance.identityKit(80),
                PlayerAppearance.identityKit(306),
            ),
            appearance.body.equipment,
        )
        assertEquals(listOf(29, 28, 27, 5, 13), appearance.body.colors)

        player.changeAppearance(CharacterAppearance.DEFAULT)
        val changed = output.message(player)
        assertNotSame(appearance, changed)
        assertEquals(PlayerAppearance.GENDER_MALE, changed.gender)
        assertSame(changed, output.message(player))
    }
}
