package emu.game.script.content

import emu.game.content.ui.config.UiComponentMap
import emu.game.loc.Loc
import emu.game.script.execution.PlayerScript
import emu.game.script.execution.PlayerScriptContext
import emu.game.script.queue.PlayerQueueType
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ScriptTrigger
import emu.game.script.trigger.ServerTriggerType
import emu.game.timer.PlayerTimerType

/** Type-safe registration DSL for feature-local Kotlin player content. */
class PlayerContent internal constructor(
    private val components: UiComponentMap,
) {
    private val triggerScripts = linkedMapOf<ScriptTrigger, PlayerScript>()
    private val queueScripts = linkedMapOf<PlayerQueueType<*>, PlayerScript>()
    private val normalTimerScripts = linkedMapOf<PlayerTimerType<*>, PlayerScript>()
    private val softTimerScripts = linkedMapOf<PlayerTimerType<*>, PlayerScript>()

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

    /** Registers content for the first cache-defined operation on [type]. */
    fun onLoc1(
        type: Int,
        body: suspend PlayerScriptContext.(Loc) -> Unit,
    ) {
        require(type in 0..0xFFFF) { "oploc1 type must fit an unsigned short" }
        bind(
            ScriptTrigger(ServerTriggerType.OPLOC1, type),
            PlayerScript("[oploc1,$type]") {
                require(argument is Loc && argument.type == type) {
                    "oploc1 $type requires its resolved loc"
                }
                body(argument)
            },
        )
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

    /** Registers a protected normal-timer script. */
    fun <A : Any> onTimer(
        type: PlayerTimerType<A>,
        body: suspend PlayerScriptContext.(A) -> Unit,
    ) {
        registerTimer(type, soft = false, typedScript("[timer,${type.name}]", type, body))
    }

    /** Registers a soft-timer script that cannot suspend. */
    fun <A : Any> onSoftTimer(
        type: PlayerTimerType<A>,
        body: PlayerScriptContext.(A) -> Unit,
    ) {
        registerTimer(type, soft = true, typedScript("[softtimer,${type.name}]", type) { body(it) })
    }

    internal fun build(): PlayerScriptRepository =
        PlayerScriptRepository(
            triggerScripts.toMap(),
            queueScripts.toMap(),
            normalTimerScripts.toMap(),
            softTimerScripts.toMap(),
        )

    private fun bind(trigger: ScriptTrigger, script: PlayerScript) {
        require(triggerScripts.putIfAbsent(trigger, script) == null) {
            "duplicate server trigger: ${script.name}"
        }
    }

    private fun <A : Any> typedScript(
        name: String,
        type: PlayerTimerType<A>,
        body: suspend PlayerScriptContext.(A) -> Unit,
    ): PlayerScript =
        PlayerScript(name) {
            require(type.argumentType.isInstance(argument)) {
                "timer ${type.name} requires ${type.argumentType.simpleName}"
            }
            @Suppress("UNCHECKED_CAST")
            body(argument as A)
        }

    private fun registerTimer(
        type: PlayerTimerType<*>,
        soft: Boolean,
        script: PlayerScript,
    ) {
        require((normalTimerScripts.keys + softTimerScripts.keys).none { it.name == type.name }) {
            "duplicate timer script: ${type.name}"
        }
        if (soft) softTimerScripts[type] = script else normalTimerScripts[type] = script
    }
}
