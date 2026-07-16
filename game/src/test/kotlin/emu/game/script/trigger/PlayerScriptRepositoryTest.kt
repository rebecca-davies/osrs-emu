package emu.game.script.trigger

import emu.game.content.ui.config.UiComponentMap
import emu.game.script.queue.PlayerQueueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PlayerScriptRepositoryTest {
    private val components =
        UiComponentMap.parse(
            """
            [components]
            "orbs:runbutton" = 10485788
            "settings_side:runmode" = 7602206
            """.trimIndent(),
        )

    @Test
    fun `button declarations build component-specific trigger entries`() {
        val scripts =
            PlayerScriptRepository.build(components) {
                onButton("orbs:runbutton", "settings_side:runmode") {}
            }

        val orb = scripts.findSpecific(ServerTriggerType.IF_BUTTON, 10485788)
        val settings = scripts.findSpecific(ServerTriggerType.IF_BUTTON, 7602206)

        assertNotNull(orb)
        assertNotNull(settings)
        assertEquals("[if_button,orbs:runbutton]", orb.name)
        assertEquals("[if_button,settings_side:runmode]", settings.name)
    }

    @Test
    fun `queue declarations are available by stable content name`() {
        val type = PlayerQueueType.unit("runmode_toggle")
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(type) {}
            }

        assertEquals("[queue,runmode_toggle]", scripts.require(type).name)
    }

    @Test
    fun `duplicate trigger declarations fail during content startup`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerScriptRepository.build(components) {
                onButton("orbs:runbutton") {}
                onButton("orbs:runbutton") {}
            }
        }
    }

    @Test
    fun `missing component names fail during content startup`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerScriptRepository.build(components) {
                onButton("missing:button") {}
            }
        }
    }
}
