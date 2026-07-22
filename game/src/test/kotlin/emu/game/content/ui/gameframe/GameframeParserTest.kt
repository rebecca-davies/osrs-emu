package emu.game.content.ui.gameframe

import emu.game.content.ui.config.UiContentCatalog
import emu.game.ui.ClientScript
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
    fun `interface state rejects work beyond its per-player capacities`() {
        val interfaces = PlayerInterfaces()
        interfaces.openTopLevel(161)
        repeat(128) { component ->
            interfaces.openModal(Component.of(161, component), 200 + component)
        }

        assertFailsWith<IllegalStateException> {
            interfaces.openModal(Component.of(161, 128), 400)
        }
        assertTrue(interfaces.closeModal())
        repeat(128) { component ->
            interfaces.openModal(Component.of(161, component), 200 + component)
        }
        assertFailsWith<IllegalStateException> { interfaces.closeModal() }
        assertTrue(interfaces.hasModal())

        interfaces.openTopLevel(161)
        interfaces.markClientSynchronized()
        repeat(256) { interfaces.runClientScript(ClientScript(1)) }
        assertFailsWith<IllegalStateException> { interfaces.runClientScript(ClientScript(1)) }
        assertEquals(256, interfaces.drainClientUpdates().size)
    }

    @Test
    fun `nested modals reserve one close operation for their shared tree`() {
        val closeBound = PlayerInterfaces()
        closeBound.openTopLevel(161)
        repeat(127) { component ->
            closeBound.openModal(Component.of(161, component), 200 + component)
        }
        assertTrue(closeBound.closeModal())
        closeBound.openModal(Component.of(161, 0), 500)
        closeBound.openModal(Component.of(500, 0), 501)

        assertTrue(closeBound.closeModal())
        assertEquals(128, generateSequence(closeBound::pollCloseTrigger).count())

        val updateBound = PlayerInterfaces()
        updateBound.openTopLevel(161)
        updateBound.openModal(Component.of(161, 0), 500)
        updateBound.openModal(Component.of(500, 0), 501)
        updateBound.markClientSynchronized()
        repeat(255) { updateBound.runClientScript(ClientScript(1)) }

        assertTrue(updateBound.closeModal())
        assertEquals(256, updateBound.drainClientUpdates().size)
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
