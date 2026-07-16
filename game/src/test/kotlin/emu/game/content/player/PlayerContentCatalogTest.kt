package emu.game.content.player

import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerContentCatalogTest {
    private val components = UiContentCatalog.load().components
    private val scripts = PlayerContentCatalog.load(components)
    private val runner = PlayerScriptRunner(scripts)

    @Test
    fun `run buttons share feature-local toggle content`() {
        val player = Player(Tile(3200, 3200, 0))
        val orb = components.require("orbs:runbutton")
        val settings = components.require("settings_side:runmode")

        runner.start(
            player,
            requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, orb.packed)),
            ButtonClick(orb.interfaceId, orb.componentId),
        )
        assertTrue(player.movement.runEnabled)

        runner.start(
            player,
            requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, settings.packed)),
            ButtonClick(settings.interfaceId, settings.componentId),
        )
        assertFalse(player.movement.runEnabled)
    }

    @Test
    fun `logout button requests the normal player lifecycle`() {
        val player = Player(Tile(3200, 3200, 0))
        val component = components.require("logout:logout")
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, component.packed))

        runner.start(player, script, ButtonClick(component.interfaceId, component.componentId))

        assertTrue(player.logoutRequested)
    }
}
