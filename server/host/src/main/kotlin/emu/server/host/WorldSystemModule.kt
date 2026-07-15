package emu.server.host

import org.koin.core.module.Module

/** Builds host-owned DI definitions for an unordered set of world-system contributions. */
internal fun worldSystemModule(bindings: WorldSystemModuleBuilder.() -> Unit): Module =
    WorldSystemModuleBuilder().apply(bindings).build()
