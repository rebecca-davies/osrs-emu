package emu.game.chat

import emu.game.cycle.CyclePhase
import emu.game.cycle.CycleProcess
import java.util.concurrent.ArrayBlockingQueue

/** Thread-safe bounded network-to-cycle chat sink. */
fun interface PlayerChatSink {
    fun submit(input: ChatInput): Boolean
}

/** Bounded chat mailbox whose declarative actions execute only in `CLIENT_INPUT`. */
class PlayerChatQueue(
    capacity: Int = DEFAULT_CAPACITY,
    private val maxPerCycle: Int = DEFAULT_MAX_PER_CYCLE,
) : PlayerChatSink {
    private val inputs: ArrayBlockingQueue<ChatInput>

    init {
        require(capacity > 0) { "chat queue capacity must be positive" }
        require(maxPerCycle > 0) { "per-cycle chat limit must be positive" }
        inputs = ArrayBlockingQueue(capacity)
    }

    override fun submit(input: ChatInput): Boolean = inputs.offer(input)

    fun cycleProcesses(actions: ChatActionRegistry): List<CycleProcess> =
        listOf(
            CycleProcess(CyclePhase.CLIENT_INPUT) {
                repeat(maxPerCycle) {
                    val input = inputs.poll() ?: return@CycleProcess
                    actions.dispatch(input)
                }
            },
        )

    private companion object {
        const val DEFAULT_CAPACITY = 32
        const val DEFAULT_MAX_PER_CYCLE = 4
    }
}
