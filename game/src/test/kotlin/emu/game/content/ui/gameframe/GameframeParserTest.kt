package emu.game.content.ui.gameframe

import emu.game.content.ui.config.UiContentCatalog
import emu.game.ui.Component
import emu.game.ui.PlayerInterfaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameframeParserTest {
    @Test
    fun `TOML defines the initial interface tree`() {
        val gameframe =
            GameframeParser.parse(
                """
                [gameframe]
                top_level = 161

                [[gameframe.sub_interfaces]]
                destination = 10551392
                interface = 162

                [[gameframe.sub_interfaces]]
                destination = 10551302
                interface = 651
                modal = true
                """.trimIndent(),
            )

        assertEquals(161, gameframe.topLevelInterface)
        assertEquals(
            listOf(
                GameframeSubInterface(Component(10551392), 162),
                GameframeSubInterface(Component(10551302), 651, modal = true),
            ),
            gameframe.subInterfaces,
        )
    }

    @Test
    fun `opening a gameframe replaces the visible interface tree`() {
        val interfaces = PlayerInterfaces()
        interfaces.openTopLevel(10)
        interfaces.openOverlay(Component.of(10, 1), 20)

        Gameframe(
            topLevelInterface = 161,
            subInterfaces = listOf(GameframeSubInterface(Component.of(161, 1), 162)),
        ).open(interfaces)

        assertFalse(interfaces.isVisible(Component.of(10, 0)))
        assertFalse(interfaces.isVisible(Component.of(20, 0)))
        assertTrue(interfaces.isVisible(Component.of(161, 0)))
        assertTrue(interfaces.isVisible(Component.of(162, 0)))
    }

    @Test
    fun `subinterfaces retain destinations and replacement removes descendants`() {
        val interfaces = PlayerInterfaces()
        val destination = Component.of(161, 1)
        val childDestination = Component.of(162, 2)
        interfaces.openTopLevel(161)
        interfaces.openOverlay(destination, 162)
        interfaces.openOverlay(childDestination, 163)

        interfaces.openModal(destination, 200)

        assertEquals(200, interfaces.subInterfaceAt(destination))
        assertFalse(interfaces.isVisible(Component.of(162, 0)))
        assertFalse(interfaces.isVisible(Component.of(163, 0)))
        assertTrue(interfaces.isVisible(Component.of(200, 0)))

        assertTrue(interfaces.closeModal())
        assertEquals(null, interfaces.subInterfaceAt(destination))
        assertFalse(interfaces.isVisible(Component.of(200, 0)))
    }

    @Test
    fun `missing and out of range gameframe values are rejected`() {
        assertFailsWith<IllegalArgumentException> { GameframeParser.parse("[components]") }
        assertFailsWith<IllegalArgumentException> {
            GameframeParser.parse(
                """
                [gameframe]
                top_level = 65536
                sub_interfaces = []
                """.trimIndent(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GameframeParser.parse(
                """
                [gameframe]
                top_level = 161

                [[gameframe.sub_interfaces]]
                destination = -1
                interface = 162
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `duplicate destinations are rejected`() {
        val source =
            """
            [gameframe]
            top_level = 161

            [[gameframe.sub_interfaces]]
            destination = 10551392
            interface = 162

            [[gameframe.sub_interfaces]]
            destination = 10551392
            interface = 163
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> { GameframeParser.parse(source) }
    }

    @Test
    fun `subinterface parents must precede their children`() {
        assertFailsWith<IllegalArgumentException> {
            Gameframe(
                topLevelInterface = 161,
                subInterfaces =
                    listOf(
                        GameframeSubInterface(Component.of(162, 1), 163),
                        GameframeSubInterface(Component.of(161, 1), 162),
                    ),
            )
        }
    }

    @Test
    fun `bundled gameframe owns revision 239 bootstrap data`() {
        val gameframe = UiContentCatalog.load().gameframe

        assertEquals(161, gameframe.topLevelInterface)
        assertEquals(25, gameframe.subInterfaces.size)
        assertEquals(2, gameframe.initialInventories.size)
        assertTrue(gameframe.subInterfaces.none(GameframeSubInterface::modal))
        gameframe.open(PlayerInterfaces())
    }
}
