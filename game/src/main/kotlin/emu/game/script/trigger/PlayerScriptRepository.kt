package emu.game.script.trigger

import emu.game.content.ui.config.UiComponentMap
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScript
import emu.game.script.queue.PlayerQueueType
import emu.game.timer.PlayerTimerType

/** Immutable O(1) index for player triggers, queues, and timers. */
class PlayerScriptRepository internal constructor(
    private val byTrigger: Map<ScriptTrigger, PlayerScript>,
    private val byQueue: Map<PlayerQueueType<*>, PlayerScript>,
    private val normalTimers: Map<PlayerTimerType<*>, PlayerScript>,
    private val softTimers: Map<PlayerTimerType<*>, PlayerScript>,
) {
    /** Finds exactly the supplied trigger combination without fallback. */
    fun findSpecific(
        type: ServerTriggerType,
        subject: Int? = null,
    ): PlayerScript? = byTrigger[ScriptTrigger(type, subject)]

    /** Resolves one named queue script or rejects invalid content during execution. */
    fun <A : Any> require(type: PlayerQueueType<A>): PlayerScript =
        requireNotNull(byQueue[type]) { "player queue script is missing: ${type.name}" }

    internal fun <A : Any> requireTimer(type: PlayerTimerType<A>, soft: Boolean): PlayerScript {
        val timers = if (soft) softTimers else normalTimers
        val prefix = if (soft) "soft timer" else "timer"
        return requireNotNull(timers[type]) { "player $prefix script is missing: ${type.name}" }
    }

    companion object {
        /** Builds an immutable repository from compiled Kotlin content declarations. */
        fun build(
            components: UiComponentMap,
            content: PlayerContent.() -> Unit,
        ): PlayerScriptRepository = PlayerContent(components).apply(content).build()
    }
}
