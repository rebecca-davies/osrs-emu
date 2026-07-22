package emu.game.script.trigger

import emu.game.content.ui.config.UiComponentMap
import emu.game.content.ui.config.UiClientConstantMap
import emu.game.content.ui.config.UiClientScriptMap
import emu.game.content.ui.config.UiContent
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScript
import emu.game.script.queue.PlayerQueueType
import emu.game.timer.PlayerTimerType

/** Immutable O(1) index for player scripts and their revision-pinned client values. */
class PlayerScriptRepository internal constructor(
    private val byTrigger: Map<ScriptTrigger, PlayerScript>,
    private val byQueue: Map<PlayerQueueType<*>, PlayerScript>,
    private val normalTimers: Map<PlayerTimerType<*>, PlayerScript>,
    private val softTimers: Map<PlayerTimerType<*>, PlayerScript>,
    internal val clientScripts: UiClientScriptMap,
    internal val clientConstants: UiClientConstantMap,
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
        ): PlayerScriptRepository =
            build(
                components,
                UiClientScriptMap.EMPTY,
                UiClientConstantMap.EMPTY,
                content,
            )

        /** Builds an immutable repository with revision-pinned UI names available to content. */
        fun build(
            ui: UiContent,
            content: PlayerContent.() -> Unit,
        ): PlayerScriptRepository =
            build(
                ui.components,
                ui.clientScripts,
                ui.clientConstants,
                content,
            )

        private fun build(
            components: UiComponentMap,
            clientScripts: UiClientScriptMap,
            clientConstants: UiClientConstantMap,
            content: PlayerContent.() -> Unit,
        ): PlayerScriptRepository =
            PlayerContent(components, clientScripts, clientConstants).apply(content).build()
    }
}
