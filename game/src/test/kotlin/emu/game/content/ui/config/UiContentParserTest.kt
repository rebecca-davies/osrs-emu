package emu.game.content.ui.config

import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.ui.gameframe.GameframeInventory
import emu.game.content.ui.gameframe.GameframeInventorySource
import emu.game.ui.Component
import emu.game.map.GameMap
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.obj.ObjCatalog
import emu.game.pathfinding.collision.OpenCollisionMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UiContentParserTest {
    @Test
    fun `one TOML document composes component names and initial interface state`() {
        val content =
            UiContentParser.parse(
                """
                [components]
                "test:button" = 65538

                [client_scripts]
                "meslayer:countdialog" = 108

                [client_constants]
                "meslayer_mode:countdialog" = 7

                [gameframe]
                top_level = 161

                [[gameframe.sub_interfaces]]
                destination = 10551392
                interface = 162

                [[gameframe.initial_inventories]]
                component = 64209
                inventory = 93
                source = "inventory"
                """.trimIndent(),
            )

        assertEquals(Component.of(1, 2), content.components.require("test:button"))
        assertEquals(108, content.clientScripts.require("meslayer:countdialog").id)
        assertEquals(7, content.clientConstants.require("meslayer_mode:countdialog"))
        assertEquals(161, content.gameframe.topLevelInterface)
        assertEquals(162, content.gameframe.subInterfaces.single().interfaceId)
        assertEquals(
            GameframeInventory(64209, 93, GameframeInventorySource.INVENTORY),
            content.gameframe.initialInventories.single(),
        )
    }

    @Test
    fun `bundled UI content is parsed and cached once`() {
        val content = UiContentCatalog.load()

        assertSame(content, UiContentCatalog.load())
        assertEquals(Component.of(387, 3), content.components.require("wornitems:pricechecker"))
        assertEquals(Component.of(387, 5), content.components.require("wornitems:deathkeep"))
        assertEquals(Component.of(387, 7), content.components.require("wornitems:call_follower"))
        PlayerContentCatalog.load(
            content,
            ObjCatalog.EMPTY,
            InfernoArena(
                GameMap(OpenCollisionMap),
                NpcCatalog.EMPTY,
                NpcList(),
                InfernoFreeModeCatalog.load(),
            ),
        )
    }
}
