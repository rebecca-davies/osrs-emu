package emu.server.host

import emu.server.world.runtime.WorldSystem
import org.koin.core.definition.Definition
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Collects uniquely keyed world-system providers without assigning execution order by declaration. */
internal class WorldSystemModuleBuilder {
    private val bindingKeys = mutableSetOf<String>()
    private val contributions = mutableListOf<Module>()

    inline fun <reified T : WorldSystem> worldSystem(
        bindingKey: String,
        noinline definition: Definition<T>,
    ) {
        registerBindingKey(bindingKey)
        addContribution(
            module {
                single<WorldSystem>(named(bindingKey)) { parameters -> definition(parameters) }
            },
        )
    }

    @PublishedApi
    internal fun registerBindingKey(bindingKey: String) {
        require(bindingKey.isNotBlank()) { "world system binding key must not be blank" }
        require(bindingKeys.add(bindingKey)) {
            "duplicate world system binding key '$bindingKey'"
        }
    }

    @PublishedApi
    internal fun addContribution(module: Module) {
        contributions += module
    }

    fun build(): Module = module { includes(contributions) }
}
