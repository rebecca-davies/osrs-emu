package emu.game.script.timer

import emu.game.content.ui.config.UiComponentMap
import emu.game.map.Tile
import emu.game.player.Player
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ServerTriggerType
import emu.game.timer.PlayerTimerType
import emu.game.ui.ButtonClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerTimerProcessTest {
    private val components =
        UiComponentMap.parse(
            "[components]\n\"test:setup\" = 65538\n\"test:busy\" = 65539",
        )

    @Test
    fun `normal and soft timers repeat from their firing world tick`() {
        val calls = mutableListOf<String>()
        val normal = PlayerTimerType("normal", String::class)
        val soft = PlayerTimerType.unit("soft")
        val scripts =
            PlayerScriptRepository.build(components) {
                onTimer(normal) { calls += "normal:$it" }
                onSoftTimer(soft) { calls += "soft" }
                onButton("test:setup") {
                    setTimer(normal, "argument", intervalTicks = 2)
                    softTimer(soft, intervalTicks = 2)
                }
            }
        val player = Player(Tile(3200, 3200, 0))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerTimerProcess(runner)

        runner.beginCycle(10)
        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65538, ButtonClick(1, 2))
        runner.beginCycle(11)
        process.process(player)
        assertEquals(emptyList(), calls)

        runner.beginCycle(12)
        process.process(player)
        runner.beginCycle(13)
        process.process(player)
        runner.beginCycle(14)
        process.process(player)

        assertEquals(listOf("normal:argument", "soft", "normal:argument", "soft"), calls)
    }

    @Test
    fun `soft timer fires while protected and normal timer waits for access`() {
        val calls = mutableListOf<String>()
        val normal = PlayerTimerType.unit("normal")
        val soft = PlayerTimerType.unit("soft")
        val scripts =
            PlayerScriptRepository.build(components) {
                onTimer(normal) { calls += "normal" }
                onSoftTimer(soft) { calls += "soft" }
                onButton("test:setup") {
                    setTimer(normal, intervalTicks = 1)
                    softTimer(soft, intervalTicks = 1)
                }
                onButton("test:busy") {
                    calls += "busy"
                    delay(2)
                    calls += "resumed"
                }
            }
        val player = Player(Tile(3200, 3200, 0))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerTimerProcess(runner)

        runner.beginCycle(0)
        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65538, ButtonClick(1, 2))
        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65539, ButtonClick(1, 3))
        runner.beginCycle(1)
        process.process(player)
        assertEquals(listOf("busy", "soft"), calls)

        runner.beginCycle(3)
        runner.resume(player)
        process.process(player)

        assertEquals(listOf("busy", "soft", "resumed", "normal", "soft"), calls)
    }

    @Test
    fun `timer mutation during processing skips cleared work without another pass`() {
        val calls = mutableListOf<String>()
        val first = PlayerTimerType.unit("first")
        val cleared = PlayerTimerType.unit("cleared")
        val scripts =
            PlayerScriptRepository.build(components) {
                onTimer(first) {
                    calls += "first"
                    clearTimer(first)
                    clearTimer(cleared)
                }
                onTimer(cleared) { calls += "cleared" }
                onButton("test:setup") {
                    setTimer(first, intervalTicks = 0)
                    setTimer(cleared, intervalTicks = 0)
                }
            }
        val player = Player(Tile(3200, 3200, 0))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerTimerProcess(runner)

        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65538, ButtonClick(1, 2))
        process.process(player)
        runner.beginCycle(1)
        process.process(player)

        assertEquals(listOf("first"), calls)
        assertFalse(first in player.timers)
        assertFalse(cleared in player.timers)
    }

    @Test
    fun `timer scheduled after a delayed resume uses the resumed world tick`() {
        val calls = mutableListOf<Long>()
        val timer = PlayerTimerType.unit("delayed")
        val scripts =
            PlayerScriptRepository.build(components) {
                onTimer(timer) { calls += worldTick }
                onButton("test:busy") {
                    delay(2)
                    setTimer(timer, intervalTicks = 2)
                }
            }
        val player = Player(Tile(3200, 3200, 0))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerTimerProcess(runner)

        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65539, ButtonClick(1, 3))
        for (worldTick in 1L..4L) {
            runner.beginCycle(worldTick)
            runner.resume(player)
            process.process(player)
        }
        assertEquals(emptyList(), calls)

        runner.beginCycle(5)
        process.process(player)

        assertEquals(listOf(5L), calls)
    }

    @Test
    fun `replacement keeps its turn while a newly added timer waits one cycle`() {
        val calls = mutableListOf<String>()
        val first = PlayerTimerType.unit("first")
        val replacement = PlayerTimerType("replacement", String::class)
        val added = PlayerTimerType.unit("added")
        val scripts =
            PlayerScriptRepository.build(components) {
                onTimer(first) {
                    calls += "first"
                    clearTimer(first)
                    setTimer(replacement, "new", intervalTicks = 0)
                    setTimer(added, intervalTicks = 0)
                }
                onTimer(replacement) {
                    calls += "replacement:$it"
                    clearTimer(replacement)
                }
                onTimer(added) {
                    calls += "added"
                    clearTimer(added)
                }
                onButton("test:setup") {
                    setTimer(first, intervalTicks = 0)
                    setTimer(replacement, "old", intervalTicks = 0)
                }
            }
        val player = Player(Tile(3200, 3200, 0))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerTimerProcess(runner)

        runner.trigger(player, ServerTriggerType.IF_BUTTON, 65538, ButtonClick(1, 2))
        process.process(player)

        assertEquals(listOf("first", "replacement:new"), calls)
        assertTrue(added in player.timers)

        runner.beginCycle(1)
        process.process(player)

        assertEquals(listOf("first", "replacement:new", "added"), calls)
    }
}
