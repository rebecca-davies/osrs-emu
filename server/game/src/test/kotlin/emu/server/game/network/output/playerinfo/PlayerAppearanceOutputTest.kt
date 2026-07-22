package emu.server.game.network.output.playerinfo

import emu.game.player.appearance.CharacterAppearance
import emu.game.player.appearance.CharacterBodyKits
import emu.game.player.appearance.CharacterColors
import emu.game.player.appearance.CharacterGender
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import emu.game.player.stat.Skill
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
        assertEquals(126, appearance.combatLevel)
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

        player.stats.setLevel(Skill.ATTACK, 1)
        val statChanged = output.message(player)
        assertNotSame(changed, statChanged)
    }

    @Test
    fun `worn objects replace body kits and hide definition-owned client slots`() {
        val player = CharacterRecord(
            id = 1,
            displayName = "Player",
            position = CharacterPosition(3_200, 3_200, 0),
            playTimeSeconds = 0,
        ).toTestPlayer()
        val fullHelm =
            ObjType(
                id = 1_000,
                name = "Full helm",
                stackable = false,
                wearpos = Wearpos.HAT,
                wearpos2 = Wearpos.HEAD,
                wearpos3 = Wearpos.JAW,
            )
        val body =
            ObjType(
                id = 2_000,
                name = "Platebody",
                stackable = false,
                wearpos = Wearpos.TORSO,
                wearpos2 = Wearpos.ARMS,
            )
        val output = PlayerAppearanceOutput(player)
        val before = output.message(player)

        player.worn.equip(fullHelm)
        player.worn.equip(body)
        val equipped = output.message(player)

        assertNotSame(before, equipped)
        assertEquals(PlayerAppearance.wornObj(fullHelm.id), equipped.body.equipment[Wearpos.HAT.slot])
        assertEquals(PlayerAppearance.wornObj(body.id), equipped.body.equipment[Wearpos.TORSO.slot])
        assertEquals(0, equipped.body.equipment[Wearpos.ARMS.slot])
        assertEquals(0, equipped.body.equipment[Wearpos.HEAD.slot])
        assertEquals(0, equipped.body.equipment[Wearpos.JAW.slot])
        assertSame(equipped, output.message(player))
    }

    @Test
    fun `object with a primary jaw position is rendered in player appearance`() {
        val player =
            CharacterRecord(
                id = 1,
                displayName = "Player",
                position = CharacterPosition(3_200, 3_200, 0),
                playTimeSeconds = 0,
            ).toTestPlayer()
        val icon =
            ObjType(
                id = 10_556,
                name = "Attacker icon",
                stackable = false,
                wearpos = Wearpos.JAW,
            )

        player.worn.equip(icon)

        assertEquals(
            PlayerAppearance.wornObj(icon.id),
            PlayerAppearanceOutput(player).message(player).body.equipment[Wearpos.JAW.slot],
        )
    }

    @Test
    fun `secondary appearance hide wins while a primary jaw object remains worn`() {
        val player =
            CharacterRecord(
                id = 1,
                displayName = "Player",
                position = CharacterPosition(3_200, 3_200, 0),
                playTimeSeconds = 0,
            ).toTestPlayer()
        val fullHelm =
            ObjType(
                id = 1_000,
                name = "Full helm",
                stackable = false,
                wearpos = Wearpos.HAT,
                wearpos2 = Wearpos.HEAD,
                wearpos3 = Wearpos.JAW,
            )
        val icon =
            ObjType(
                id = 10_556,
                name = "Attacker icon",
                stackable = false,
                wearpos = Wearpos.JAW,
            )

        player.worn.equip(fullHelm)
        player.worn.equip(icon)
        val equipment = PlayerAppearanceOutput(player).message(player).body.equipment

        assertEquals(icon.id, player.worn[Wearpos.JAW]?.obj?.type)
        assertEquals(PlayerAppearance.wornObj(fullHelm.id), equipment[Wearpos.HAT.slot])
        assertEquals(0, equipment[Wearpos.JAW.slot])
    }
}
