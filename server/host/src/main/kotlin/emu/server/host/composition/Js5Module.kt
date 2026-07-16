package emu.server.host.composition

import emu.cache.store.Store
import emu.server.js5.Js5Server
import emu.server.js5.Js5Service
import emu.server.js5.config.Js5ExecutionConfig
import emu.server.js5.handler.Js5RequestHandler
import emu.transport.codec.CodecRepository
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Defines the JS5 service from host-owned cache, protocol, and execution capabilities. */
internal fun js5Module(
    store: Store,
    codecs: CodecRepository,
    config: Js5ExecutionConfig,
) = module {
    single { Js5RequestHandler(store) }
    single<Js5Service> { Js5Server(codecs, get(), config) } onClose { it?.close() }
}
