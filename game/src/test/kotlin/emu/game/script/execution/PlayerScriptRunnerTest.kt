package emu.game.script.execution

import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiComponentMap
import emu.game.map.Tile
import emu.game.player.Player
import emu.game.script.queue.PlayerQueueType
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerScriptRunnerTest {
    private val components = UiComponentMap.parse("[components]\n\"test:button\" = 65538")

    @Test
    fun `compiled Kotlin script runs against player state`() {
        val scripts =
            PlayerScriptRepository.build(components) {
                onButton("test:button") {
                    player.varps[PlayerVarpCatalog.RUN_MODE] = 1
                    player.movement.runEnabled = true
                }
            }
        val player = player()
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))

        val started = PlayerScriptRunner(scripts).start(player, script, ButtonClick(1, 2))

        assertTrue(started)
        assertTrue(player.movement.runEnabled)
        assertEquals(1, player.varps[PlayerVarpCatalog.RUN_MODE])
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `delay resumes at the absolute 2004scape world tick`() {
        val calls = mutableListOf<String>()
        val scripts =
            PlayerScriptRepository.build(components) {
                onButton("test:button") {
                    calls += "start"
                    delay(2)
                    calls += "resume"
                }
            }
        val player = player()
        val runner = PlayerScriptRunner(scripts)
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))

        runner.start(player, script, ButtonClick(1, 2))
        assertEquals(listOf("start"), calls)
        assertTrue(player.isAccessProtected)

        runner.beginCycle(1)
        runner.resume(player)
        assertEquals(listOf("start"), calls)
        runner.beginCycle(2)
        runner.resume(player)
        assertEquals(listOf("start"), calls)
        runner.beginCycle(3)
        runner.resume(player)

        assertEquals(listOf("start", "resume"), calls)
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `script queue commands enqueue named scripts with authentic priority`() {
        val later = PlayerQueueType.unit("later")
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(later) {}
                onButton("test:button") { strongQueue(later, delayTicks = 3) }
            }
        val player = player()
        val runner = PlayerScriptRunner(scripts)
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))

        runner.start(player, script, ButtonClick(1, 2))

        var modalClosed = false
        player.actionQueue.processPrimaryAndWeak(
            canAccess = { false },
            closeModal = { modalClosed = true },
            execute = {},
        )

        assertTrue(modalClosed)
        assertEquals(1, player.actionQueue.primarySize)
    }

    @Test
    fun `unprotected script does not release another active scripts protected access`() {
        val calls = mutableListOf<String>()
        val scripts =
            PlayerScriptRepository.build(components) {
                onButton("test:button") {
                    calls += "protected"
                    delay(1)
                    calls += "resumed"
                }
                onQueue(PlayerQueueType.unit("unprotected")) { calls += "unprotected" }
            }
        val player = player()
        val runner = PlayerScriptRunner(scripts)
        val protected = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))
        val unprotected = scripts.require(PlayerQueueType.unit("unprotected"))
        runner.start(player, protected, ButtonClick(1, 2))

        runner.start(player, unprotected, protect = false)

        assertTrue(player.isAccessProtected)
        runner.beginCycle(1)
        runner.resume(player)
        assertTrue(player.isAccessProtected)
        runner.beginCycle(2)
        runner.resume(player)
        assertEquals(listOf("protected", "unprotected", "resumed"), calls)
        assertFalse(player.isAccessProtected)
    }

    @Test
    fun `zero delay waits one tick and the RuneScript null sentinel is rejected`() {
        val calls = mutableListOf<String>()
        val scripts =
            PlayerScriptRepository.build(components) {
                onButton("test:button") {
                    calls += "start"
                    delay(0)
                    calls += "resume"
                }
            }
        val player = player()
        val runner = PlayerScriptRunner(scripts)
        val script = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))

        runner.start(player, script, ButtonClick(1, 2))
        runner.resume(player)
        assertEquals(listOf("start"), calls)
        runner.beginCycle(1)
        runner.resume(player)

        assertEquals(listOf("start", "resume"), calls)

        val invalidScripts =
            PlayerScriptRepository.build(components) {
                onButton("test:button") { delay(-1) }
            }
        val invalid = requireNotNull(invalidScripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))
        assertFailsWith<IllegalArgumentException> {
            PlayerScriptRunner(invalidScripts).start(player(), invalid, ButtonClick(1, 2))
        }
    }

    private fun player() = Player(Tile(3200, 3200, 0))
}
