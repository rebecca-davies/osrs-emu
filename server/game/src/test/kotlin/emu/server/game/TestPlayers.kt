package emu.server.game

import emu.game.map.Tile
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.npc.NpcUid
import emu.game.player.Player
import emu.game.player.PlayerChatFilters
import emu.game.player.StaffModLevel
import emu.game.player.appearance.CharacterAppearance
import emu.persistence.character.model.CharacterRecord
import emu.server.game.world.player.interaction.NpcInteractionTargetResolver

internal fun testPlayer(
    position: Tile = Tile(3_200, 3_200),
    id: Long = 1,
    index: Int = 1,
    displayName: String = "Player",
    staffModLevel: StaffModLevel = StaffModLevel.NONE,
    appearance: CharacterAppearance = CharacterAppearance.DEFAULT,
): Player =
    Player(
        id = id,
        index = index,
        displayName = displayName,
        staffModLevel = staffModLevel,
        initialPosition = position,
        initialAppearance = appearance,
    )

internal fun CharacterRecord.toTestPlayer(
    index: Int = 1,
    staffModLevel: StaffModLevel = StaffModLevel.NONE,
): Player =
    Player(
        id = id,
        index = index,
        displayName = displayName,
        staffModLevel = staffModLevel,
        initialPosition = Tile(position.x, position.y, position.plane),
        savedVarps = varps,
        initialChatFilters =
            PlayerChatFilters(
                chatFilters.publicMode,
                chatFilters.privateMode,
                chatFilters.tradeMode,
            ),
        initialAppearance = appearance,
    )

internal fun testNpcTargets(
    npcs: NpcList = NpcList(),
    types: NpcCatalog = NpcCatalog.EMPTY,
    localUid: (Player, Int) -> NpcUid? = { _, index ->
        npcs[index]?.let { npc -> NpcUid(npc.index, npc.uid) }
    },
): NpcInteractionTargetResolver = NpcInteractionTargetResolver(npcs, types, localUid)
