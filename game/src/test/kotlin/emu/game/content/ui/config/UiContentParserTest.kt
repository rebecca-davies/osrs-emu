package emu.game.content.ui.config

import emu.game.content.player.PlayerContentCatalog
import emu.game.content.ui.gameframe.GameframeInventory
import emu.game.content.ui.gameframe.GameframeInventorySource
import emu.game.ui.Component
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
        PlayerContentCatalog.load(content)
    }
}
