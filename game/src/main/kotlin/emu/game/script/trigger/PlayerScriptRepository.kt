package emu.game.script.trigger

import emu.game.content.ui.config.UiComponentMap
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScript
import emu.game.script.queue.PlayerQueueType

/** Immutable O(1) server-trigger and named-queue script index. */
class PlayerScriptRepository internal constructor(
    private val byTrigger: Map<ScriptTrigger, PlayerScript>,
    private val byQueue: Map<PlayerQueueType<*>, PlayerScript>,
) {
    /** Finds exactly the supplied trigger combination without fallback. */
    fun findSpecific(
        type: ServerTriggerType,
        subject: Int? = null,
    ): PlayerScript? = byTrigger[ScriptTrigger(type, subject)]

    /** Resolves one named queue script or rejects invalid content during execution. */
    fun <A : Any> require(type: PlayerQueueType<A>): PlayerScript =
        requireNotNull(byQueue[type]) { "player queue script is missing: ${type.name}" }

    companion object {
        /** Builds an immutable repository from compiled Kotlin content declarations. */
        fun build(
            components: UiComponentMap,
            content: PlayerContent.() -> Unit,
        ): PlayerScriptRepository = PlayerContent(components).apply(content).build()
    }
}
