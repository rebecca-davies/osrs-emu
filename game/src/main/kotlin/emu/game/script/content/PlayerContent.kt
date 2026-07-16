package emu.game.script.content

import emu.game.content.ui.config.UiComponentMap
import emu.game.script.execution.PlayerScript
import emu.game.script.execution.PlayerScriptContext
import emu.game.script.queue.PlayerQueueType
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ScriptTrigger
import emu.game.script.trigger.ServerTriggerType

/** Type-safe registration DSL for feature-local Kotlin player content. */
class PlayerContent internal constructor(
    private val components: UiComponentMap,
) {
    private val triggerScripts = linkedMapOf<ScriptTrigger, PlayerScript>()
    private val queueScripts = linkedMapOf<PlayerQueueType<*>, PlayerScript>()

    /** Registers the same content body for one or more ordinary interface buttons. */
    fun onButton(
        vararg componentNames: String,
        body: suspend PlayerScriptContext.() -> Unit,
    ) {
        require(componentNames.isNotEmpty()) { "an if_button script requires a component" }
        for (componentName in componentNames) {
            val component = components.require(componentName)
            val script = PlayerScript("[if_button,$componentName]", body)
            bind(ScriptTrigger(ServerTriggerType.IF_BUTTON, component.packed), script)
        }
    }

    /** Registers content run when a modal rooted at the named interface closes. */
    fun onClose(
        componentName: String,
        body: suspend PlayerScriptContext.() -> Unit,
    ) {
        val interfaceId = components.require(componentName).interfaceId
        bind(
            ScriptTrigger(ServerTriggerType.IF_CLOSE, subject = interfaceId),
            PlayerScript("[if_close,$componentName]", body),
        )
    }

    /** Registers the global player-login trigger. */
    fun onLogin(body: suspend PlayerScriptContext.() -> Unit) {
        bind(ScriptTrigger(ServerTriggerType.LOGIN), PlayerScript("[login]", body))
    }

    /** Registers the global player-logout trigger. */
    fun onLogout(body: suspend PlayerScriptContext.() -> Unit) {
        bind(ScriptTrigger(ServerTriggerType.LOGOUT), PlayerScript("[logout]", body))
    }

    /** Registers a named script that may be placed on a player action queue. */
    fun <A : Any> onQueue(
        type: PlayerQueueType<A>,
        body: suspend PlayerScriptContext.(A) -> Unit,
    ) {
        val script =
            PlayerScript("[queue,${type.name}]") {
                require(type.argumentType.isInstance(argument)) {
                    "queue ${type.name} requires ${type.argumentType.simpleName}"
                }
                @Suppress("UNCHECKED_CAST")
                body(argument as A)
            }
        require(queueScripts.keys.none { it.name == type.name }) {
            "duplicate queue script: ${type.name}"
        }
        queueScripts[type] = script
    }

    internal fun build(): PlayerScriptRepository =
        PlayerScriptRepository(triggerScripts.toMap(), queueScripts.toMap())

    private fun bind(trigger: ScriptTrigger, script: PlayerScript) {
        require(triggerScripts.putIfAbsent(trigger, script) == null) {
            "duplicate server trigger: ${script.name}"
        }
    }
}
