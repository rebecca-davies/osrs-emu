package emu.server.world.player

import emu.game.content.ui.UiComponentMap
import emu.game.content.ui.UiContentCatalog
import emu.game.queue.PlayerActionPriority
import emu.game.script.PlayerQueueType
import emu.game.script.PlayerScriptRepository
import emu.game.script.PlayerScriptRequest
import emu.game.script.PlayerScriptRunner
import emu.game.script.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.game.ui.Component
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.server.world.entity.WorldPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayerScriptProcessTest {
    private val components =
        UiComponentMap.parse(
            "[components]\n\"test:button\" = 65538\n\"test:modal\" = 13107200",
        )

    @Test
    fun `player phase runs primary weak and engine scripts in order with typed arguments`() {
        val calls = mutableListOf<String>()
        val primary = PlayerQueueType("primary", String::class)
        val weak = PlayerQueueType.unit("weak")
        val engine = PlayerQueueType.unit("engine")
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(primary) { calls += it }
                onQueue(weak) { calls += "weak" }
                onQueue(engine) { calls += "engine" }
            }
        val player = player()
        player.actionQueue.add(PlayerScriptRequest(scripts.require(primary), "primary"))
        player.actionQueue.add(PlayerScriptRequest(scripts.require(weak)), PlayerActionPriority.WEAK)
        player.actionQueue.add(PlayerScriptRequest(scripts.require(engine)), PlayerActionPriority.ENGINE)

        val runner = PlayerScriptRunner(scripts)
        PlayerScriptProcess(runner, PlayerTriggerProcess(runner)).process(player)

        assertEquals(listOf("primary", "weak", "engine"), calls)
    }

    @Test
    fun `strong script closes modal and clears weak scripts before execution`() {
        val calls = mutableListOf<String>()
        val strong = PlayerQueueType.unit("strong")
        val weak = PlayerQueueType.unit("weak")
        val scripts =
            PlayerScriptRepository.build(components) {
                onClose("test:modal") { calls += "close" }
                onQueue(strong) { calls += "strong" }
                onQueue(weak) { calls += "weak" }
            }
        val player = player()
        player.interfaces.openModal(Component.of(161, 1), 200)
        player.actionQueue.add(PlayerScriptRequest(scripts.require(weak)), PlayerActionPriority.WEAK)
        player.actionQueue.add(PlayerScriptRequest(scripts.require(strong)), PlayerActionPriority.STRONG)

        val runner = PlayerScriptRunner(scripts)
        PlayerScriptProcess(runner, PlayerTriggerProcess(runner)).process(player)

        assertEquals(listOf("close", "strong"), calls)
        assertFalse(player.interfaces.isVisible(Component.of(200, 0)))
    }

    @Test
    fun `client-input script delay uses the absolute world tick before queued scripts`() {
        val calls = mutableListOf<String>()
        val queued = PlayerQueueType.unit("queued")
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(queued) { calls += "queued" }
                onButton("test:button") {
                    calls += "start"
                    delay(1)
                    calls += "resume"
                }
            }
        val player = player()
        val runner = PlayerScriptRunner(scripts)
        val button = requireNotNull(scripts.findSpecific(ServerTriggerType.IF_BUTTON, 65538))
        runner.start(player, button, ButtonClick(1, 2))
        player.actionQueue.add(PlayerScriptRequest(scripts.require(queued)))

        val process = PlayerScriptProcess(runner, PlayerTriggerProcess(runner))
        process.beginCycle(0)
        process.process(player)
        assertEquals(listOf("start"), calls)
        process.beginCycle(1)
        process.process(player)
        assertEquals(listOf("start"), calls)
        process.beginCycle(2)
        process.process(player)

        assertEquals(listOf("start", "resume", "queued"), calls)
    }

    @Test
    fun `queue script delay starts from the player phase world tick`() {
        val calls = mutableListOf<String>()
        val queued = PlayerQueueType.unit("queued")
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(queued) {
                    calls += "start"
                    delay(0)
                    calls += "resume"
                }
            }
        val player = player()
        player.actionQueue.add(PlayerScriptRequest(scripts.require(queued)))
        val runner = PlayerScriptRunner(scripts)
        val process = PlayerScriptProcess(runner, PlayerTriggerProcess(runner))

        process.beginCycle(7)
        process.process(player)
        assertEquals(listOf("start"), calls)
        process.beginCycle(8)
        process.process(player)

        assertEquals(listOf("start", "resume"), calls)
    }

    private fun player() =
        WorldPlayer(
            PlayerRecord(1, "Player1", PlayerPosition(3200, 3200, 0), 0),
        ).apply { activate(UiContentCatalog.load().gameframe) }
}
